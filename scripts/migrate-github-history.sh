#!/usr/bin/env bash
set -euo pipefail

SOURCE_REPO="${SOURCE_REPO:-softeerbootcamp-8th/WEB-TEAM2-2gether}"
TARGET_REPO="${TARGET_REPO:-hyeonyway/WEB-TEAM2-2gether}"
TARGET_REF="${TARGET_REF:-main}"
MODE="dry-run"
ONLY="all"
INCLUDE_COMMENTS=1
MAX_ITEMS=0
TMP_DIR=""
MARKERS=""
GIT_DIR=""

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/lib/github-history-core.sh"
source "$ROOT_DIR/scripts/lib/github-history-issues.sh"
source "$ROOT_DIR/scripts/lib/github-history-prs.sh"

usage() {
  cat <<'EOF'
Usage: bash scripts/migrate-github-history.sh [options]

  --apply           Actually create archive issues/comments in the fork.
  --issues-only     Copy issues only.
  --prs-only        Archive PRs only.
  --no-comments     Skip comments and reviews.
  --max-items N     At most N issues and N PRs (0 = unlimited).
  --target-ref REF  Ref used for Git fallback (default: main).
  -h, --help        Show help.

Defaults:
  source = softeerbootcamp-8th/WEB-TEAM2-2gether
  target = hyeonyway/WEB-TEAM2-2gether

Override repositories with SOURCE_REPO and TARGET_REPO environment variables.
Without --apply this command is read-only.
EOF
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --apply) MODE="apply" ;;
      --issues-only) ONLY="issues" ;;
      --prs-only) ONLY="prs" ;;
      --no-comments) INCLUDE_COMMENTS=0 ;;
      --max-items) shift; [[ $# -gt 0 && "$1" =~ ^[0-9]+$ ]] || { printf 'invalid --max-items\n' >&2; exit 2; }; MAX_ITEMS="$1" ;;
      --target-ref) shift; [[ $# -gt 0 ]] || { printf 'missing --target-ref\n' >&2; exit 2; }; TARGET_REF="$1" ;;
      -h|--help) usage; exit 0 ;;
      *) printf 'unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
    esac
    shift
  done
}

init_migration() {
  command -v gh >/dev/null || { printf 'gh is required\n' >&2; exit 1; }
  command -v jq >/dev/null || { printf 'jq is required\n' >&2; exit 1; }
  command -v git >/dev/null || { printf 'git is required\n' >&2; exit 1; }
  gh auth status >/dev/null 2>&1 || { printf 'run: gh auth login\n' >&2; exit 1; }
  gh api "repos/$TARGET_REPO" >/dev/null || { printf 'target repository is not accessible: %s\n' "$TARGET_REPO" >&2; exit 1; }

  TMP_DIR="$(mktemp -d)"; MARKERS="$TMP_DIR/markers"; : > "$MARKERS"
  trap 'rm -rf "$TMP_DIR"' EXIT INT TERM
  gh api --paginate "repos/$TARGET_REPO/issues?state=all&per_page=100" > "$TMP_DIR/target-items.json"
  jq -r '.[] | .body // empty' "$TMP_DIR/target-items.json" | grep -oE '<!-- github-history-migration:(issue|pr):[^>]+ -->' >> "$MARKERS" || true
}

marker_seen() { grep -Fqx "$1" "$MARKERS" 2>/dev/null; }
remember_marker() { printf '%s\n' "$1" >> "$MARKERS"; }

ensure_label() {
  local name="$1" color="${2:-ededed}" description="${3:-}"
  gh api "repos/$TARGET_REPO/labels/$(printf '%s' "$name" | jq -sRr @uri)" >/dev/null 2>&1 && return 0
  if [[ "$MODE" == "dry-run" ]]; then printf '  [dry-run] label: %s\n' "$name"; return 0; fi
  gh label create "$name" --repo "$TARGET_REPO" --color "$color" --description "$description" >/dev/null
}

ensure_source_labels() {
  local json="$1" row
  while IFS= read -r row; do
    [[ -z "$row" ]] && continue
    ensure_label "$(jq -r '.name' <<< "$row")" "$(jq -r '.color // "ededed"' <<< "$row")" "$(jq -r '.description // ""' <<< "$row")"
  done < <(jq -c '.labels[]?' <<< "$json")
}

create_archive_issue() {
  local title="$1" body="$2" labels_json="$3" result label
  if [[ "$MODE" == "dry-run" ]]; then printf '  [dry-run] issue: %s\n' "$title" >&2; printf '0'; return 0; fi
  result="$(gh issue create --repo "$TARGET_REPO" --title "$title" --body "$body")"
  while IFS= read -r label; do [[ -n "$label" ]] && gh issue edit "$result" --repo "$TARGET_REPO" --add-label "$label" >/dev/null; done < <(jq -r '.[]' <<< "$labels_json")
  printf '%s' "${result##*/}"
}

add_archive_comment() {
  local number="$1" body="$2"
  [[ "$MODE" == "dry-run" ]] && { printf '    [dry-run] comment\n'; return 0; }
  gh issue comment "$number" --repo "$TARGET_REPO" --body "$body" >/dev/null
}

close_archive_issue() {
  local number="$1"
  [[ "$MODE" == "dry-run" ]] && return 0
  gh issue close "$number" --repo "$TARGET_REPO" >/dev/null
}

main() {
  parse_args "$@"; init_migration
  printf 'Source: %s\nTarget: %s\nMode: %s\n' "$SOURCE_REPO" "$TARGET_REPO" "$MODE"
  [[ "$ONLY" != "prs" ]] && migrate_issues
  [[ "$ONLY" != "issues" ]] && migrate_prs
  if [[ "$MODE" == "dry-run" ]]; then printf '\nDry-run only. Re-run with --apply to write.\n'; else printf '\nArchive run finished. Hidden markers make reruns idempotent.\n'; fi
}

if [[ "${MIGRATION_TESTING:-0}" != "1" ]]; then main "$@"; fi
