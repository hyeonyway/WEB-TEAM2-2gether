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
DUP_LABEL = "migration-duplicate"


def run(cmd, data=None, check=True):
    p = subprocess.run(cmd, input=data, text=True, capture_output=True)
    if check and p.returncode != 0:
        print(p.stderr, file=sys.stderr)
        raise RuntimeError("command failed: " + " ".join(cmd))
    return p


def api(endpoint, method=None, payload=None):
    cmd = ["gh", "api"]
    if method:
        cmd += ["--method", method]
    cmd.append(endpoint)
    if payload is not None:
        cmd += ["--input", "-"]
        p = run(cmd, json.dumps(payload))
    else:
        p = run(cmd)
    return json.loads(p.stdout) if p.stdout.strip() else None


def paged(endpoint):
    p = run(["gh", "api", "--paginate", "--slurp", endpoint])
    pages = json.loads(p.stdout)
    return [item for page in pages for item in page]


def marker(kind, number):
    return f"<!-- github-history-migration:{kind}:{SOURCE}#{number} -->"


def snapshot():
    groups = defaultdict(list)
    tombstones = []
    for item in paged(f"repos/{TARGET}/issues?state=all&per_page=100"):
        if "pull_request" in item:
            continue
        body = item.get("body") or ""
        m = MARKER_RE.search(body)
        if m:
            groups[m.group(0)].append(item)
        if any(x.get("name") == DUP_LABEL for x in item.get("labels", [])):
            tombstones.append(item)
    return groups, tombstones


def expected():
    items = paged(f"repos/{SOURCE}/issues?state=all&per_page=100")
    issue_markers = {
        marker("issue", i["number"]) for i in items if "pull_request" not in i
    }
    prs = paged(f"repos/{SOURCE}/pulls?state=all&per_page=100")
    pr_markers = {marker("pr", p["number"]) for p in prs}
    return issue_markers, pr_markers


def ensure_label():
    p = run(["gh", "api", f"repos/{TARGET}/labels/{DUP_LABEL}"], check=False)
    if p.returncode == 0:
        return
    run([
        "gh", "label", "create", DUP_LABEL,
        "--repo", TARGET,
        "--color", "B60205",
        "--description", "Duplicate artifact created during GitHub history migration",
    ])


def tombstone_body(item, canonical_number, marker_value):
    body = item.get("body") or ""
    m = MARKER_RE.fullmatch(marker_value)
    kind = "archive"
    source_number = "unknown"
    if m:
        kind, source_number = m.group(1), m.group(2)
    replacement = (
        f"<!-- github-history-migration-duplicate:{kind}:{SOURCE}#{source_number};"
        f"canonical-target-issue:{canonical_number} -->"
    )
    body = body.replace(marker_value, replacement, 1)
    warning = (
        f"> [!WARNING]\n"
        f"> This is a duplicate artifact created during the one-time GitHub history migration.  \n"
        f"> Canonical archive: **#{canonical_number}**. Do not use this copy as the preserved record.\n\n"
    )
    if body.startswith("> [!WARNING]"):
        return body
    return warning + body


def update_duplicate(item, canonical_number, marker_value):
    title = item.get("title") or ""
    if not title.startswith("[DUPLICATE ARCHIVE] "):
        title = "[DUPLICATE ARCHIVE] " + title
    labels = [x.get("name") for x in item.get("labels", []) if x.get("name")]
    if DUP_LABEL not in labels:
        labels.append(DUP_LABEL)
    payload = {
        "title": title,
        "body": tombstone_body(item, canonical_number, marker_value),
        "state": "closed",
        "state_reason": "duplicate",
        "labels": labels,
    }
    api(f"repos/{TARGET}/issues/{item['number']}", "PATCH", payload)


def main():
    ensure_label()
    groups, _ = snapshot()
    duplicates = []
    for marker_value, items in groups.items():
        items.sort(key=lambda x: x["number"])
        canonical = items[0]
        for dup in items[1:]:
            duplicates.append((marker_value, canonical, dup))

    print(f"Duplicate valid-marker artifacts to tombstone: {len(duplicates)}")
    for idx, (marker_value, canonical, dup) in enumerate(duplicates, 1):
        update_duplicate(dup, canonical["number"], marker_value)
        print(f"[{idx}/{len(duplicates)}] tombstoned #{dup['number']} -> canonical #{canonical['number']}")

    groups, tombstones = snapshot()
    actual = set(groups)
    actual_issues = {m for m in actual if ":issue:" in m}
    actual_prs = {m for m in actual if ":pr:" in m}
    duplicates_left = sum(max(0, len(v)-1) for v in groups.values())
    expected_issues, expected_prs = expected()

    missing_issues = expected_issues - actual_issues
    missing_prs = expected_prs - actual_prs
    extra_issues = actual_issues - expected_issues
    extra_prs = actual_prs - expected_prs

    print(
        f"FINAL CANONICAL MARKERS issues={len(actual_issues)}/{len(expected_issues)} "
        f"prs={len(actual_prs)}/{len(expected_prs)} duplicates={duplicates_left} "
        f"tombstones={len(tombstones)}"
    )

    if missing_issues:
        print("missing issues:", sorted(missing_issues)[:20], file=sys.stderr)
    if missing_prs:
        print("missing prs:", sorted(missing_prs)[:20], file=sys.stderr)
    if extra_issues:
        print("unexpected issues:", sorted(extra_issues)[:20], file=sys.stderr)
    if extra_prs:
        print("unexpected prs:", sorted(extra_prs)[:20], file=sys.stderr)

    if duplicates_left or missing_issues or missing_prs or extra_issues or extra_prs:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
