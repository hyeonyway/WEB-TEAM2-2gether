#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MIGRATION_TESTING=1 source "$ROOT/scripts/migrate-github-history.sh"

failures=0
assert_eq() { local e="$1" a="$2" n="$3"; if [[ "$e" == "$a" ]]; then printf 'PASS: %s\n' "$n"; else printf 'FAIL: %s\n  expected: %s\n  actual:   %s\n' "$n" "$e" "$a" >&2; failures=$((failures+1)); fi; }
assert_has() { local h="$1" n="$2" name="$3"; if [[ "$h" == *"$n"* ]]; then printf 'PASS: %s\n' "$name"; else printf 'FAIL: %s\n  missing: %s\n' "$name" "$n" >&2; failures=$((failures+1)); fi; }

assert_eq 42 "$(extract_pr_number_from_commit_message $'Merge pull request #42 from team/x\n\nfeat: wallet')" "merge commit PR number"
assert_eq 314 "$(extract_pr_number_from_commit_message 'feat: session (#314)')" "squash PR number"
assert_eq '' "$(extract_pr_number_from_commit_message 'chore: plain')" "non-PR commit ignored"
assert_eq 'feat: wallet' "$(extract_pr_title_from_commit_message $'Merge pull request #42 from team/x\n\nfeat: wallet')" "merge title"
assert_eq 'feat: session' "$(extract_pr_title_from_commit_message 'feat: session (#314)')" "squash title"

if (( failures > 0 )); then printf '\n%d test(s) failed.\n' "$failures" >&2; exit 1; fi
printf '\nAll migration helper tests passed.\n'
