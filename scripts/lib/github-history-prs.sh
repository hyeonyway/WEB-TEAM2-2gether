#!/usr/bin/env bash

pr_file_table_from_api() {
  local number="$1" row path status additions deletions
  if ! gh api --paginate "repos/$SOURCE_REPO/pulls/$number/files?per_page=100" > "$TMP_DIR/pr-files.json" 2>/dev/null; then
    printf '_Changed-file details unavailable._\n'; return 0
  fi
  printf '| File | Status | + | - |\n| --- | --- | ---: | ---: |\n'
  while IFS= read -r row; do
    path="$(jq -r '.filename' <<< "$row" | sed 's/|/\\|/g')"
    status="$(jq -r '.status' <<< "$row")"
    additions="$(jq -r '.additions' <<< "$row")"
    deletions="$(jq -r '.deletions' <<< "$row")"
    printf '| `%s` | %s | %s | %s |\n' "$path" "$status" "$additions" "$deletions"
  done < <(jq -c '.[]' "$TMP_DIR/pr-files.json")
}

build_pr_archive_body_from_api() {
  local json="$1" number marker author created closed merged url body state base head sha changed additions deletions table
  number="$(jq -r '.number' <<< "$json")"; marker="$(pr_marker "$SOURCE_REPO" "$number")"
  author="$(jq -r '.user.login // "unknown"' <<< "$json")"; created="$(jq -r '.created_at // "unknown"' <<< "$json")"
  closed="$(jq -r '.closed_at // ""' <<< "$json")"; merged="$(jq -r '.merged_at // ""' <<< "$json")"
  url="$(jq -r '.html_url // ""' <<< "$json")"; body="$(jq -r '.body // ""' <<< "$json")"
  state="$(jq -r '.state // "unknown"' <<< "$json")"; base="$(jq -r '.base.ref // "unknown"' <<< "$json")"
  head="$(jq -r '.head.ref // "unknown"' <<< "$json")"; sha="$(jq -r '.merge_commit_sha // ""' <<< "$json")"
  changed="$(jq -r '.changed_files // "?"' <<< "$json")"; additions="$(jq -r '.additions // "?"' <<< "$json")"
  deletions="$(jq -r '.deletions // "?"' <<< "$json")"; table="$(pr_file_table_from_api "$number")"
  cat <<EOF_BODY
$marker

> Archived PR from **$SOURCE_REPO#$number**  
> Original author: **@$author** · State: **$state** · Merged: **${merged:-no}**  
> Created: **$created** · Closed: **${closed:-open}**  
> Base: **$base** · Head: **$head** · Merge commit: **${sha:-unknown}**  
> Changed files: **$changed** · Additions: **+$additions** · Deletions: **-$deletions**  
> Original URL: $url

## Original description

$body

## Changed files

$table
EOF_BODY
}

append_pr_discussion_section() {
  local kind="$1" row="$2" out="$3" author at url body extra path line
  author="$(jq -r '.user.login // "unknown"' <<< "$row")"
  at="$(jq -r '.submitted_at // .created_at // "unknown"' <<< "$row")"
  url="$(jq -r '.html_url // ""' <<< "$row")"
  body="$(jq -r '.body // ""' <<< "$row")"
  extra=""
  [[ "$kind" == "review" ]] && extra=" · $(jq -r '.state // "COMMENTED"' <<< "$row")"
  if [[ "$kind" == "inline" ]]; then
    path="$(jq -r '.path // ""' <<< "$row")"
    line="$(jq -r '.line // .original_line // ""' <<< "$row")"
    extra=" · $path${line:+:$line}"
  fi
  [[ -z "$body" && "$kind" == "review" ]] && body="_(review submitted without a body)_"
  cat >> "$out" <<EOF_SECTION

---

### Original PR $kind$extra

**@$author** · $at  
$url

$body
EOF_SECTION
}

copy_pr_discussion() {
  local number="$1" target="$2" detail="$3" kind endpoint row count out has_any=0
  (( INCLUDE_COMMENTS == 1 )) || return 0
  out="$TMP_DIR/pr-discussion.md"
  : > "$out"

  for kind in conversation review inline; do
    case "$kind" in
      conversation)
        count="$(jq -r '.comments // 0' <<< "$detail")"
        (( count > 0 )) || continue
        endpoint="issues/$number/comments"
        ;;
      review)
        endpoint="pulls/$number/reviews"
        ;;
      inline)
        count="$(jq -r '.review_comments // 0' <<< "$detail")"
        (( count > 0 )) || continue
        endpoint="pulls/$number/comments"
        ;;
    esac

    gh api --paginate "repos/$SOURCE_REPO/$endpoint?per_page=100" > "$TMP_DIR/pr-$kind.json" 2>/dev/null || continue
    while IFS= read -r row; do
      has_any=1
      append_pr_discussion_section "$kind" "$row" "$out"
    done < <(jq -c '.[]' "$TMP_DIR/pr-$kind.json")
  done

  (( has_any == 1 )) || return 0

  # GitHub comment bodies have a size limit. Split the aggregated archive into
  # conservative chunks while preserving every original discussion entry.
  python3 - "$out" <<'PY' | while IFS= read -r chunk; do
import pathlib, sys
text = pathlib.Path(sys.argv[1]).read_text()
limit = 50000
parts = []
while text:
    if len(text.encode()) <= limit:
        parts.append(text); break
    cut = min(len(text), 45000)
    while len(text[:cut].encode()) > limit:
        cut -= 1000
    sep = text.rfind("\n---\n", 0, cut)
    if sep > 0: cut = sep
    parts.append(text[:cut])
    text = text[cut:]
for p in parts:
    print(p.replace("\x1e", "" ).replace("\n", "\x1e"))
PY
    add_archive_comment "$target" "$(printf '%s' "$chunk" | tr '\036' '\n')"
  done
}

prepare_target_git() {
  [[ -n "$GIT_DIR" ]] && return 0
  GIT_DIR="$TMP_DIR/target-repo"
  gh repo clone "$TARGET_REPO" "$GIT_DIR" -- --quiet >/dev/null 2>&1 || return 1
  git -C "$GIT_DIR" fetch --quiet origin "$TARGET_REF" || true
}

target_history_ref() {
  if git -C "$GIT_DIR" rev-parse --verify --quiet "origin/$TARGET_REF" >/dev/null; then printf 'origin/%s' "$TARGET_REF"; else printf '%s' "$TARGET_REF"; fi
}

find_pr_commit() {
  local number="$1" ref sha
  prepare_target_git || return 1; ref="$(target_history_ref)"
  sha="$(git -C "$GIT_DIR" log "$ref" --grep="^Merge pull request #$number" --format='%H' -n 1)"
  [[ -n "$sha" ]] || sha="$(git -C "$GIT_DIR" log "$ref" --grep="(#$number)$" --format='%H' -n 1)"
  printf '%s' "$sha"
}

build_pr_archive_body_from_git() {
  local number="$1" sha="$2" message title author date base stats marker
  message="$(git -C "$GIT_DIR" show -s --format='%B' "$sha")"; title="$(extract_pr_title_from_commit_message "$message")"
  author="$(git -C "$GIT_DIR" show -s --format='%an <%ae>' "$sha")"; date="$(git -C "$GIT_DIR" show -s --format='%aI' "$sha")"
  base="$(git -C "$GIT_DIR" rev-parse "$sha^1" 2>/dev/null || true)"; stats="$(git -C "$GIT_DIR" diff --numstat "$base" "$sha" 2>/dev/null || true)"
  marker="$(pr_marker "$SOURCE_REPO" "$number")"
  cat <<EOF_BODY
$marker

> **Reconstructed from the fork's Git history** because source PR metadata was unavailable.  
> Original PR number inferred from commit message: **#$number**  
> Commit: **$sha** · Author: **$author** · Date: **$date**  
> Fork commit: https://github.com/$TARGET_REPO/commit/$sha

## Reconstructed title

$title

## Changed files reconstructed from Git

$(numstat_table "$stats")
EOF_BODY
}

archive_pr_from_git() {
  local number="$1" sha="$2" marker message title target
  marker="$(pr_marker "$SOURCE_REPO" "$number")"; marker_seen "$marker" && return 0
  message="$(git -C "$GIT_DIR" show -s --format='%B' "$sha")"; title="$(extract_pr_title_from_commit_message "$message")"
  ensure_label archived-pr 6f42c1 "Archived pull request"; ensure_label reconstructed-from-git d4c5f9 "Reconstructed from Git history"
  target="$(create_archive_issue "[Archived PR #$number] $title" "$(build_pr_archive_body_from_git "$number" "$sha")" '["archived-pr","reconstructed-from-git"]')"
  close_archive_issue "$target" completed; remember_marker "$marker"
}

scan_prs_from_git() {
  local ref sha subject number count=0
  prepare_target_git || { printf 'WARN: could not clone target for Git fallback.\n' >&2; return 0; }; ref="$(target_history_ref)"
  while IFS=$'\t' read -r sha subject; do
    (( MAX_ITEMS > 0 && count >= MAX_ITEMS )) && break
    number="$(extract_pr_number_from_commit_message "$subject")"; [[ -z "$number" ]] && continue
    marker_seen "$(pr_marker "$SOURCE_REPO" "$number")" && continue
    archive_pr_from_git "$number" "$sha"; ((count+=1))
  done < <(git -C "$GIT_DIR" log "$ref" --format=$'%H\t%s')
}

migrate_prs() {
  local item detail number marker title labels target state merged sha count=0
  printf '== Pull requests ==\n'
  if ! gh api --paginate "repos/$SOURCE_REPO/pulls?state=all&per_page=100" > "$TMP_DIR/source-prs.json" 2>/dev/null; then
    printf 'WARN: source PR API unavailable; using Git fallback.\n' >&2; scan_prs_from_git; return 0
  fi
  while IFS= read -r item; do
    (( MAX_ITEMS > 0 && count >= MAX_ITEMS )) && break
    number="$(jq -r '.number' <<< "$item")"; marker="$(pr_marker "$SOURCE_REPO" "$number")"; title="$(jq -r '.title' <<< "$item")"
    if marker_seen "$marker"; then printf '[PR #%s] already archived\n' "$number"; continue; fi
    if ! detail="$(gh api "repos/$SOURCE_REPO/pulls/$number" 2>/dev/null)"; then
      sha="$(find_pr_commit "$number" || true)"; [[ -n "$sha" ]] && archive_pr_from_git "$number" "$sha" || printf 'WARN: PR #%s not detectable in Git.\n' "$number" >&2
      ((count+=1)); continue
    fi
    printf '[PR #%s] %s\n' "$number" "$title"
    ensure_label archived-pr 6f42c1 "Archived pull request"
    labels="$(jq -c '[.labels[]?.name] + ["archived-pr"] | unique' <<< "$item")"
    target="$(create_archive_issue "[Archived PR #$number] $title" "$(build_pr_archive_body_from_api "$detail")" "$labels")"
    copy_pr_discussion "$number" "$target" "$detail"
    state="$(jq -r '.state // "closed"' <<< "$detail")"; merged="$(jq -r '.merged // false' <<< "$detail")"
    [[ "$state" == "closed" || "$merged" == "true" ]] && close_archive_issue "$target" completed
    remember_marker "$marker"; ((count+=1))
  done < <(jq -c '.[]' "$TMP_DIR/source-prs.json")
}
