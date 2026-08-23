#!/usr/bin/env bash

issue_marker() { printf '<!-- github-history-migration:issue:%s#%s -->' "$1" "$2"; }
pr_marker() { printf '<!-- github-history-migration:pr:%s#%s -->' "$1" "$2"; }

extract_pr_number_from_commit_message() {
  local message="$1" number
  number="$(printf '%s\n' "$message" | sed -nE 's/^Merge pull request #([0-9]+).*/\1/p' | head -n 1)"
  if [[ -n "$number" ]]; then printf '%s' "$number"; return 0; fi
  printf '%s\n' "$message" | sed -nE 's/.*\(#([0-9]+)\)[[:space:]]*$/\1/p' | head -n 1
}

extract_pr_title_from_commit_message() {
  local message="$1" first title
  first="$(printf '%s\n' "$message" | head -n 1)"
  if printf '%s\n' "$first" | grep -Eq '^Merge pull request #[0-9]+'; then
    title="$(printf '%s\n' "$message" | tail -n +2 | sed '/^[[:space:]]*$/d' | head -n 1)"
    printf '%s' "${title:-$first}"
  else
    printf '%s' "$first" | sed -E 's/[[:space:]]*\(#[0-9]+\)[[:space:]]*$//'
  fi
}

numstat_table() {
  local text="$1" added deleted path
  printf '| File | + | - |\n| --- | ---: | ---: |\n'
  while IFS=$'\t' read -r added deleted path; do
    [[ -z "${path:-}" ]] && continue
    path="$(printf '%s' "$path" | sed 's/|/\\|/g')"
    if [[ "$added" == "-" || "$deleted" == "-" ]]; then
      printf '| `%s` | binary | binary |\n' "$path"
    else
      printf '| `%s` | %s | %s |\n' "$path" "$added" "$deleted"
    fi
  done <<< "$text"
}
