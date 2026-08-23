#!/usr/bin/env python3
import concurrent.futures
import json
import os
import re
import subprocess
import sys
from collections import defaultdict

SOURCE = os.environ.get("SOURCE_REPO", "softeerbootcamp-8th/WEB-TEAM2-2gether")
TARGET = os.environ.get("TARGET_REPO", "hyeonyway/WEB-TEAM2-2gether")
MARKER_RE = re.compile(r"<!-- github-history-migration:(?:issue|pr):[^>]+ -->")
MAX_BODY = 62000
MAX_COMMENT = 60000


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
    return [x for page in pages for x in page]


def marker(kind, number):
    return f"<!-- github-history-migration:{kind}:{SOURCE}#{number} -->"


def target_snapshot():
    issues = [i for i in paged(f"repos/{TARGET}/issues?state=all&per_page=100") if "pull_request" not in i]
    markers = set()
    groups = defaultdict(list)
    for i in issues:
        m = MARKER_RE.search(i.get("body") or "")
        if m:
            markers.add(m.group(0))
            groups[m.group(0)].append(i)
    return issues, markers, groups


def pr_number_from_url(url):
    try:
        return int(url.rstrip("/").split("/")[-1])
    except Exception:
        return None


def git_numstat(pr):
    sha = pr.get("merge_commit_sha")
    if not pr.get("merged_at") or not sha:
        return None
    probe = run(["git", "cat-file", "-e", f"{sha}^{{commit}}"], check=False)
    if probe.returncode != 0:
        return None
    parent = run(["git", "rev-parse", f"{sha}^1"], check=False)
    if parent.returncode != 0:
        return None
    diff = run(["git", "diff", "--numstat", parent.stdout.strip(), sha], check=False)
    if diff.returncode != 0:
        return None
    rows = []
    for line in diff.stdout.splitlines():
        parts = line.split("\t", 2)
        if len(parts) != 3:
            continue
        a, d, path = parts
        rows.append((path, a, d))
    return rows


def build_body(pr):
    num = pr["number"]
    author = (pr.get("user") or {}).get("login") or "unknown"
    source_url = pr.get("html_url") or f"https://github.com/{SOURCE}/pull/{num}"
    rows = git_numstat(pr)
    if rows is None:
        file_section = "_Changed-file list is preserved by the original PR and, for merged PRs, by the fork's Git history._"
    else:
        table = ["| File | + | - |", "| --- | ---: | ---: |"]
        table += [f"| `{path}` | {a} | {d} |" for path, a, d in rows]
        file_section = "\n".join(table)
    body = f"""{marker('pr', num)}

> Archived PR from **{SOURCE}#{num}**  
> Original author: **@{author}** · State: **{pr.get('state','unknown')}** · Merged: **{pr.get('merged_at') or 'no'}**  
> Created: **{pr.get('created_at') or 'unknown'}** · Closed: **{pr.get('closed_at') or 'open'}**  
> Base: **{(pr.get('base') or {}).get('ref') or 'unknown'}** · Head: **{(pr.get('head') or {}).get('ref') or 'unknown'}** · Merge commit: **{pr.get('merge_commit_sha') or 'unknown'}**  
> Original URL: {source_url}

## Original description

{pr.get('body') or ''}

## Changed files

{file_section}
"""
    if len(body) > MAX_BODY:
        body = body[:MAX_BODY-250] + "\n\n_[Archive body truncated at GitHub's body limit. The original PR URL and Git history retain the remaining detail.]_\n"
    return body


def entry_comment(c, kind):
    author = (c.get("user") or {}).get("login") or "unknown"
    at = c.get("submitted_at") or c.get("created_at") or "unknown"
    url = c.get("html_url") or ""
    extra = ""
    if kind == "review":
        extra = f" · {c.get('state') or 'COMMENTED'}"
    elif kind == "inline":
        path = c.get("path") or "unknown path"
        line = c.get("line") or c.get("original_line")
        extra = f" · `{path}`{f':{line}' if line else ''}"
    return at, f"### Original PR {kind}{extra}\n\n**@{author}** · {at}  \n{url}\n\n{c.get('body') or ''}"


def split_discussion(entries):
    entries.sort(key=lambda x: x[0])
    text = "\n\n---\n\n".join(x[1] for x in entries if x[1].strip())
    chunks = []
    while text:
        if len(text) <= MAX_COMMENT:
            chunks.append(text); break
        cut = text.rfind("\n---\n", 0, MAX_COMMENT)
        if cut < 1000:
            cut = MAX_COMMENT
        chunks.append(text[:cut])
        text = text[cut:]
    return chunks


def fetch_reviews(number):
    try:
        return paged(f"repos/{SOURCE}/pulls/{number}/reviews?per_page=100")
    except Exception as e:
        print(f"WARN reviews unavailable for PR #{number}: {e}", file=sys.stderr)
        return []


def create_one(pr, conversations, inline_comments, reviews):
    num = pr["number"]
    labels = [x["name"] for x in pr.get("labels", [])] + ["archived-pr"]
    payload = {"title": f"[Archived PR #{num}] {pr['title']}", "body": build_body(pr), "labels": list(dict.fromkeys(labels))}
    created = api(f"repos/{TARGET}/issues", "POST", payload)
    target_num = created["number"]

    entries = []
    for c in conversations.get(num, []):
        entries.append(entry_comment(c, "conversation comment"))
    for r in reviews:
        if (r.get("body") or "").strip():
            entries.append(entry_comment(r, "review"))
    for c in inline_comments.get(num, []):
        entries.append(entry_comment(c, "inline review comment"))
    for chunk in split_discussion(entries):
        api(f"repos/{TARGET}/issues/{target_num}/comments", "POST", {"body": chunk})

    if pr.get("state") == "closed" or pr.get("merged_at"):
        api(f"repos/{TARGET}/issues/{target_num}", "PATCH", {"state": "closed", "state_reason": "completed"})
    return num, target_num


def try_delete_duplicates():
    _, _, groups = target_snapshot()
    dupes = []
    for items in groups.values():
        items.sort(key=lambda x: x["number"])
        dupes.extend(items[1:])
    print(f"Duplicate migration issues found: {len(dupes)}")
    if not dupes:
        return True
    for issue in dupes:
        query = "mutation($id:ID!){deleteIssue(input:{issueId:$id}){clientMutationId}}"
        p = run(["gh", "api", "graphql", "--input", "-"], json.dumps({"query": query, "variables": {"id": issue["node_id"]}}), check=False)
        if p.returncode != 0:
            print("WARN: GraphQL deleteIssue unavailable; duplicate deletion deferred", file=sys.stderr)
            return False
    return True


def main():
    _, existing, _ = target_snapshot()
    pulls = paged(f"repos/{SOURCE}/pulls?state=all&per_page=100")
    pending = [p for p in pulls if marker('pr', p['number']) not in existing]
    print(f"Source PRs={len(pulls)} pending={len(pending)} existing_markers={len(existing)}")

    issue_comments = paged(f"repos/{SOURCE}/issues/comments?per_page=100")
    conversations = defaultdict(list)
    for c in issue_comments:
        n = pr_number_from_url(c.get("issue_url") or "")
        if n:
            conversations[n].append(c)

    inline = defaultdict(list)
    for c in paged(f"repos/{SOURCE}/pulls/comments?per_page=100"):
        n = pr_number_from_url(c.get("pull_request_url") or "")
        if n:
            inline[n].append(c)

    completed = 0
    for pr in pending:
        reviews = fetch_reviews(pr["number"])
        src, dst = create_one(pr, conversations, inline, reviews)
        completed += 1
        print(f"[{completed}/{len(pending)}] PR #{src} -> issue #{dst}")

    _, final_markers, _ = target_snapshot()
    issue_markers = sum(1 for m in final_markers if ":issue:" in m)
    pr_markers = sum(1 for m in final_markers if ":pr:" in m)
    print(f"UNIQUE MARKERS before dedupe: issue={issue_markers} pr={pr_markers} total={len(final_markers)}")
    if issue_markers != 290 or pr_markers != 334:
        raise SystemExit(f"marker verification failed: issue={issue_markers}, pr={pr_markers}")

    try_delete_duplicates()
    _, final_markers, groups = target_snapshot()
    duplicates = sum(max(0, len(v)-1) for v in groups.values())
    print(f"FINAL UNIQUE MARKERS issue={sum(1 for m in final_markers if ':issue:' in m)} pr={sum(1 for m in final_markers if ':pr:' in m)} duplicates={duplicates}")

if __name__ == "__main__":
    main()
