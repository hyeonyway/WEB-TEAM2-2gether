# GitHub history migration

This utility archives GitHub collaboration history from the original dbidding repository into the personal fork.

Verified on 2026-08-23:

- Source: `softeerbootcamp-8th/WEB-TEAM2-2gether`
- Target fork: `hyeonyway/WEB-TEAM2-2gether`
- Both repositories use `main` as the default branch.
- Both `main` branches pointed to commit `77c1e766c97d6ed6fc09e16e29846702999fdedc` when this utility was prepared.
- The source Issues and Pull Request APIs were accessible at verification time.
- Final verification expects exactly one archive marker per source item: 290 Issues and 334 Pull Requests.

The migration script is dry-run by default. It recreates source issues as target issues and archives historical PRs as closed issues. When source PR metadata is no longer readable, it falls back to merge/squash commit messages and diffs from the fork's Git history.

Run the script with `--max-items 3` first to preview a small sample, then add `--apply` only when ready to create target issues.
