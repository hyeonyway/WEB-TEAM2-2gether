#!/usr/bin/env bash

build_issue_archive_body() {
  local json="$1" number author created closed url body marker
  number="$(jq -r '.number' <<< "$json")"
  author="$(jq -r '.user.login // "unknown"' <<< "$json")"
  created="$(jq -r '.created_at // "unknown"' <<< "$json")"
  closed="$(jq -r '.closed_at // ""' <<< "$json")"
  url="$(jq -r '.html_url // ""' <<< "$json")"
  body="$(jq -r '.body // ""' <<< "$json")"
  marker="$(issue_marker "$SOURCE_REPO" "$number")"
  cat <<EOF_BODY
$marker

> Archived from **$SOURCE_REPO#$number**  
> Original author: **@$author** · Created: **$created** · Closed: **${closed:-open}**  
> Original URL: $url  
> Unqualified references such as \`#123\` below refer to the **source repository**.

---

$body
EOF_BODY
}

archive_issue_comment_body() {
  local json="$1" author created url body
  author="$(jq -r '.user.login // "unknown"' <<< "$json")"
  created="$(jq -r '.created_at // "unknown"' <<< "$json")"
  url="$(jq -r '.html_url // ""' <<< "$json")"
  body="$(jq -r '.body // ""' <<< "$json")"
  cat <<EOF_BODY
> **Original issue comment** by @$author · $created  
> $url

$body
EOF_BODY
}

migrate_issues() {
  local item number marker title labels target state reason comments_count count=0 comment
  printf '== Issues ==\n'
  if ! gh api --paginate "repos/$SOURCE_REPO/issues?state=all&per_page=100" > "$TMP_DIR/source-issues.json" 2>/dev/null; then
    printf 'WARN: source Issues API unavailable; issues cannot be reconstructed from Git history.\n' >&2
    return 0
  fi

  while IFS= read -r item; do
    (( MAX_ITEMS > 0 && count >= MAX_ITEMS )) && break
    number="$(jq -r '.number' <<< "$item")"
    marker="$(issue_marker "$SOURCE_REPO" "$number")"
    title="$(jq -r '.title' <<< "$item")"
    if marker_seen "$marker"; then
      printf '[issue #%s] already migrated\n' "$number"
      continue
    fi

    printf '[issue #%s] %s\n' "$number" "$title"
    labels="$(jq -c '[.labels[]?.name]' <<< "$item")"
    target="$(create_archive_issue "$title" "$(build_issue_archive_body "$item")" "$labels")"

    comments_count="$(jq -r '.comments // 0' <<< "$item")"
    if (( INCLUDE_COMMENTS == 1 && comments_count > 0 )); then
      if gh api --paginate "repos/$SOURCE_REPO/issues/$number/comments?per_page=100" > "$TMP_DIR/issue-comments.json" 2>/dev/null; then
        while IFS= read -r comment; do
          add_archive_comment "$target" "$(archive_issue_comment_body "$comment")"
        done < <(jq -c '.[]' "$TMP_DIR/issue-comments.json")
      fi
    fi

    state="$(jq -r '.state // "open"' <<< "$item")"
    reason="$(jq -r '.state_reason // "completed"' <<< "$item")"
    [[ "$state" == "closed" ]] && close_archive_issue "$target" "$reason"
    remember_marker "$marker"
    ((count+=1))
  done < <(jq -c '.[] | select(.pull_request | not)' "$TMP_DIR/source-issues.json")
}
