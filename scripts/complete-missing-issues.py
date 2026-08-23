#!/usr/bin/env python3
import json
import os
import re
import subprocess
import sys
from collections import defaultdict

SOURCE = os.environ.get("SOURCE_REPO", "softeerbootcamp-8th/WEB-TEAM2-2gether")
TARGET = os.environ.get("TARGET_REPO", "hyeonyway/WEB-TEAM2-2gether")
MAX_BODY = 62000
MAX_COMMENT = 60000
MARKER_RE = re.compile(
    rf"<!-- github-history-migration:(issue|pr):{re.escape(SOURCE)}#(\d+) -->"
)


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


def issue_number_from_url(url):
    try:
        return int((url or "").rstrip("/").split("/")[-1])
    except Exception:
        return None


def target_markers():
    markers = set()
    for item in paged(f"repos/{TARGET}/issues?state=all&per_page=100"):
        if "pull_request" in item:
            continue
        match = MARKER_RE.search(item.get("body") or "")
        if match:
            markers.add(match.group(0))
    return markers


def archive_body(issue):
    number = issue["number"]
    author = (issue.get("user") or {}).get("login") or "unknown"
    labels = ", ".join(f"`{x['name']}`" for x in issue.get("labels", [])) or "none"
    assignees = ", ".join(
        f"@{x.get('login')}" for x in issue.get("assignees", []) if x.get("login")
    ) or "none"
    milestone = (issue.get("milestone") or {}).get("title") or "none"
    closed = issue.get("closed_at") or "open"
    state_reason = issue.get("state_reason") or "none"
    source_url = issue.get("html_url") or f"https://github.com/{SOURCE}/issues/{number}"
    body = f"""{marker('issue', number)}

> Archived from **{SOURCE}#{number}**  
> Original author: **@{author}** · Created: **{issue.get('created_at') or 'unknown'}** · Closed: **{closed}**  
> State: **{issue.get('state') or 'unknown'}** · State reason: **{state_reason}**  
> Labels: {labels} · Assignees: {assignees} · Milestone: **{milestone}**  
> Original URL: {source_url}  
> Unqualified references such as `#123` below refer to the **source repository**.

---

{issue.get('body') or ''}
"""
    if len(body) > MAX_BODY:
        body = (
            body[: MAX_BODY - 260]
            + "\n\n_[Archive body truncated at GitHub's body limit. The original issue URL retains the remaining detail.]_\n"
        )
    return body


def archive_comment(comment):
    author = (comment.get("user") or {}).get("login") or "unknown"
    created = comment.get("created_at") or "unknown"
    url = comment.get("html_url") or ""
    body = comment.get("body") or ""
    text = f"""### Original issue comment

**@{author}** · {created}  
{url}

{body}
"""
    if len(text) <= MAX_COMMENT:
        return [text]
    chunks = []
    remaining = text
    while remaining:
        chunks.append(remaining[:MAX_COMMENT])
        remaining = remaining[MAX_COMMENT:]
    return chunks


def main():
    existing = target_markers()
    source_items = paged(f"repos/{SOURCE}/issues?state=all&per_page=100")
    source_issues = [i for i in source_items if "pull_request" not in i]
    pending = [i for i in source_issues if marker("issue", i["number"]) not in existing]

    print(
        f"Source issues={len(source_issues)} "
        f"existing_issue_markers={sum(1 for x in existing if ':issue:' in x)} "
        f"pending={len(pending)}"
    )

    all_comments = paged(f"repos/{SOURCE}/issues/comments?per_page=100")
    comments_by_issue = defaultdict(list)
    for c in all_comments:
        number = issue_number_from_url(c.get("issue_url"))
        if number:
            comments_by_issue[number].append(c)

    completed = 0
    for issue in sorted(pending, key=lambda x: x["number"]):
        number = issue["number"]
        labels = list(dict.fromkeys(x["name"] for x in issue.get("labels", [])))
        created = api(
            f"repos/{TARGET}/issues",
            "POST",
            {
                "title": issue["title"],
                "body": archive_body(issue),
                "labels": labels,
            },
        )
        target_number = created["number"]

        for c in sorted(comments_by_issue.get(number, []), key=lambda x: x.get("created_at") or ""):
            for chunk in archive_comment(c):
                api(
                    f"repos/{TARGET}/issues/{target_number}/comments",
                    "POST",
                    {"body": chunk},
                )

        if issue.get("state") == "closed":
            reason = issue.get("state_reason")
            if reason not in {"completed", "not_planned"}:
                reason = "completed"
            api(
                f"repos/{TARGET}/issues/{target_number}",
                "PATCH",
                {"state": "closed", "state_reason": reason},
            )

        completed += 1
        print(f"[{completed}/{len(pending)}] issue #{number} -> issue #{target_number}")

    final_markers = target_markers()
    issue_markers = {m for m in final_markers if ":issue:" in m}
    source_issue_markers = {marker("issue", i["number"]) for i in source_issues}
    missing = sorted(source_issue_markers - issue_markers)
    unexpected = sorted(issue_markers - source_issue_markers)
    print(
        f"UNIQUE ISSUE MARKERS={len(issue_markers)} "
        f"source={len(source_issue_markers)} "
        f"missing={len(missing)} unexpected={len(unexpected)}"
    )
    if missing or unexpected or len(issue_markers) != len(source_issue_markers):
        print("missing markers:", missing[:20], file=sys.stderr)
        print("unexpected markers:", unexpected[:20], file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
