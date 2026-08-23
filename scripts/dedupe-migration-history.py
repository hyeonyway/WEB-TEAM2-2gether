#!/usr/bin/env python3
import json
import os
import re
import subprocess
import sys
from collections import defaultdict

SOURCE = os.environ.get("SOURCE_REPO", "softeerbootcamp-8th/WEB-TEAM2-2gether")
TARGET = os.environ.get("TARGET_REPO", "hyeonyway/WEB-TEAM2-2gether")
MARKER_RE = re.compile(
    rf"<!-- github-history-migration:(issue|pr):{re.escape(SOURCE)}#(\d+) -->"
)


def run(cmd, data=None, check=True):
    p = subprocess.run(cmd, input=data, text=True, capture_output=True)
    if check and p.returncode != 0:
        print(p.stderr, file=sys.stderr)
        raise RuntimeError("command failed: " + " ".join(cmd))
    return p


def paged(endpoint):
    p = run(["gh", "api", "--paginate", "--slurp", endpoint])
    pages = json.loads(p.stdout)
    return [item for page in pages for item in page]


def marker(kind, number):
    return f"<!-- github-history-migration:{kind}:{SOURCE}#{number} -->"


def target_snapshot():
    groups = defaultdict(list)
    for item in paged(f"repos/{TARGET}/issues?state=all&per_page=100"):
        if "pull_request" in item:
            continue
        match = MARKER_RE.search(item.get("body") or "")
        if match:
            groups[match.group(0)].append(item)
    return groups


def expected_markers():
    source_items = paged(f"repos/{SOURCE}/issues?state=all&per_page=100")
    issue_numbers = [i["number"] for i in source_items if "pull_request" not in i]
    pulls = paged(f"repos/{SOURCE}/pulls?state=all&per_page=100")
    pr_numbers = [p["number"] for p in pulls]
    return (
        {marker("issue", n) for n in issue_numbers},
        {marker("pr", n) for n in pr_numbers},
    )


def delete_issue(node_id, number):
    query = "mutation($id:ID!){deleteIssue(input:{issueId:$id}){clientMutationId}}"
    p = run(
        ["gh", "api", "graphql", "-f", f"query={query}", "-F", f"id={node_id}"],
        check=False,
    )
    if p.returncode != 0:
        print(
            f"FAILED deleting duplicate target issue #{number}\n{p.stderr}",
            file=sys.stderr,
        )
        return False
    return True


def main():
    groups = target_snapshot()
    duplicates = []
    for marker_value, items in groups.items():
        items.sort(key=lambda x: x["number"])
        canonical = items[0]
        for duplicate in items[1:]:
            duplicates.append((marker_value, canonical, duplicate))

    print(f"Duplicate migration issues to delete: {len(duplicates)}")
    failures = []
    for idx, (marker_value, canonical, duplicate) in enumerate(duplicates, start=1):
        if not delete_issue(duplicate["node_id"], duplicate["number"]):
            failures.append(duplicate["number"])
        else:
            print(
                f"[{idx}/{len(duplicates)}] deleted #{duplicate['number']} "
                f"(kept #{canonical['number']})"
            )

    if failures:
        raise SystemExit(f"duplicate deletion failed for target issues: {failures[:20]}")

    groups = target_snapshot()
    expected_issues, expected_prs = expected_markers()
    actual = set(groups)
    actual_issues = {m for m in actual if ":issue:" in m}
    actual_prs = {m for m in actual if ":pr:" in m}
    duplicates_left = sum(max(0, len(items) - 1) for items in groups.values())

    missing_issues = expected_issues - actual_issues
    missing_prs = expected_prs - actual_prs
    unexpected_issues = actual_issues - expected_issues
    unexpected_prs = actual_prs - expected_prs

    print(
        "FINAL VERIFICATION "
        f"issues={len(actual_issues)}/{len(expected_issues)} "
        f"prs={len(actual_prs)}/{len(expected_prs)} "
        f"duplicates={duplicates_left}"
    )

    if missing_issues:
        print("missing issue markers:", sorted(missing_issues)[:20], file=sys.stderr)
    if missing_prs:
        print("missing PR markers:", sorted(missing_prs)[:20], file=sys.stderr)
    if unexpected_issues:
        print("unexpected issue markers:", sorted(unexpected_issues)[:20], file=sys.stderr)
    if unexpected_prs:
        print("unexpected PR markers:", sorted(unexpected_prs)[:20], file=sys.stderr)

    if (
        duplicates_left
        or missing_issues
        or missing_prs
        or unexpected_issues
        or unexpected_prs
    ):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
