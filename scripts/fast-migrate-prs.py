#!/usr/bin/env python3
import json
import os
import re
import subprocess
import sys
from collections import defaultdict
from datetime import datetime

SOURCE = os.environ.get("SOURCE_REPO", "softeerbootcamp-8th/WEB-TEAM2-2gether")
TARGET = os.environ.get("TARGET_REPO", "hyeonyway/WEB-TEAM2-2gether")
MARKER_RE = re.compile(r"<!-- github-history-migration:(?:issue|pr):[^>]+ -->")
MAX_BODY = 62000
MAX_COMMENT = 60000


def gh_graphql(query, variables=None):
    payload = {"query": query, "variables": variables or {}}
    p = subprocess.run(
        ["gh", "api", "graphql", "--input", "-"],
        input=json.dumps(payload), text=True, capture_output=True
    )
    if p.returncode != 0:
        print(p.stderr, file=sys.stderr)
        raise RuntimeError("gh graphql failed")
    return json.loads(p.stdout)


def target_snapshot():
    q = """
    query($owner:String!,$name:String!,$after:String){
      repository(owner:$owner,name:$name){
        id
        labels(first:100){nodes{id name}}
        issues(first:100,after:$after,orderBy:{field:CREATED_AT,direction:ASC}){
          nodes{id number body state}
          pageInfo{hasNextPage endCursor}
        }
      }
    }
    """
    owner, name = TARGET.split("/", 1)
    after = None
    repo_id = None
    labels = {}
    issues = []
    while True:
        data = gh_graphql(q, {"owner": owner, "name": name, "after": after})["data"]["repository"]
        repo_id = data["id"]
        labels.update({n["name"]: n["id"] for n in data["labels"]["nodes"]})
        issues.extend(data["issues"]["nodes"])
        page = data["issues"]["pageInfo"]
        if not page["hasNextPage"]:
            break
        after = page["endCursor"]
    markers = set()
    for issue in issues:
        m = MARKER_RE.search(issue.get("body") or "")
        if m:
            markers.add(m.group(0))
    return repo_id, labels, issues, markers


SOURCE_QUERY = """
query($owner:String!,$name:String!,$after:String){
  repository(owner:$owner,name:$name){
    pullRequests(first:10,after:$after,orderBy:{field:CREATED_AT,direction:DESC},states:[OPEN,CLOSED,MERGED]){
      nodes{
        number title body state merged mergedAt createdAt closedAt
        baseRefName headRefName additions deletions changedFiles
        author{login}
        mergeCommit{oid}
        commits{totalCount}
        labels(first:50){nodes{name}}
        files(first:100){nodes{path additions deletions changeType}}
        comments(first:100){nodes{author{login} createdAt url body}}
        reviews(first:100){nodes{author{login} submittedAt state body}}
        reviewThreads(first:100){nodes{isResolved isOutdated path line originalLine comments(first:100){nodes{author{login} createdAt url body path line originalLine}}}}
      }
      pageInfo{hasNextPage endCursor}
    }
  }
}
"""


def source_pages():
    owner, name = SOURCE.split("/", 1)
    after = None
    while True:
        data = gh_graphql(SOURCE_QUERY, {"owner": owner, "name": name, "after": after})["data"]["repository"]["pullRequests"]
        yield data["nodes"]
        page = data["pageInfo"]
        if not page["hasNextPage"]:
            break
        after = page["endCursor"]


def marker(number):
    return f"<!-- github-history-migration:pr:{SOURCE}#{number} -->"


def fmt_file(f):
    status = f.get("changeType", "UNKNOWN").lower()
    return f"| `{f['path']}` | {status} | {f.get('additions', 0)} | {f.get('deletions', 0)} |"


def build_body(pr):
    num = pr["number"]
    merged = pr.get("mergedAt") or "no"
    merge_sha = (pr.get("mergeCommit") or {}).get("oid") or "unknown"
    author = (pr.get("author") or {}).get("login") or "unknown"
    source_url = f"https://github.com/{SOURCE}/pull/{num}"
    files = pr.get("files", {}).get("nodes", [])
    table = ["| File | Status | + | - |", "| --- | --- | ---: | ---: |"] + [fmt_file(f) for f in files]
    if pr.get("changedFiles", 0) > len(files):
        table.append(f"\n_Only the first {len(files)} file rows are listed here; Git history in the fork preserves the full diff._")
    body = f"""{marker(num)}

> Archived PR from **{SOURCE}#{num}**  
> Original author: **@{author}** · State: **{pr.get('state','unknown').lower()}** · Merged: **{merged}**  
> Created: **{pr.get('createdAt') or 'unknown'}** · Closed: **{pr.get('closedAt') or 'open'}**  
> Base: **{pr.get('baseRefName') or 'unknown'}** · Head: **{pr.get('headRefName') or 'unknown'}** · Merge commit: **{merge_sha}**  
> Commits: **{pr.get('commits',{}).get('totalCount','?')}** · Changed files: **{pr.get('changedFiles','?')}** · Additions: **+{pr.get('additions','?')}** · Deletions: **-{pr.get('deletions','?')}**  
> Original URL: {source_url}

## Original description

{pr.get('body') or ''}

## Changed files

{chr(10).join(table)}
"""
    if len(body) > MAX_BODY:
        body = body[:MAX_BODY-200] + "\n\n_[Archive body truncated at GitHub's body limit. Full diff remains in Git history and the original PR URL above.]_\n"
    return body


def discussion(pr):
    entries = []
    for c in pr.get("comments", {}).get("nodes", []):
        entries.append((c.get("createdAt") or "", f"### Original PR conversation comment\n\n**@{(c.get('author') or {}).get('login') or 'unknown'}** · {c.get('createdAt') or 'unknown'}  \n{c.get('url') or ''}\n\n{c.get('body') or ''}"))
    for r in pr.get("reviews", {}).get("nodes", []):
        if not (r.get("body") or "").strip():
            continue
        entries.append((r.get("submittedAt") or "", f"### Original PR review · {r.get('state') or 'COMMENTED'}\n\n**@{(r.get('author') or {}).get('login') or 'unknown'}** · {r.get('submittedAt') or 'unknown'}\n\n{r.get('body') or ''}"))
    for t in pr.get("reviewThreads", {}).get("nodes", []):
        loc = t.get("path") or "unknown path"
        line = t.get("line") or t.get("originalLine")
        status = "resolved" if t.get("isResolved") else "unresolved"
        if t.get("isOutdated"):
            status += " / outdated"
        parts = [f"### Original review thread — `{loc}`{f' line {line}' if line else ''}\n\nStatus: **{status}**"]
        ts = ""
        for c in t.get("comments", {}).get("nodes", []):
            ts = ts or (c.get("createdAt") or "")
            parts.append(f"\n**@{(c.get('author') or {}).get('login') or 'unknown'}** · {c.get('createdAt') or 'unknown'}  \n{c.get('url') or ''}\n\n{c.get('body') or ''}")
        entries.append((ts, "\n".join(parts)))
    entries.sort(key=lambda x: x[0])
    text = "\n\n---\n\n".join(e[1] for e in entries)
    if not text:
        return []
    chunks = []
    while text:
        if len(text) <= MAX_COMMENT:
            chunks.append(text)
            break
        cut = text.rfind("\n---\n", 0, MAX_COMMENT)
        if cut < 1000:
            cut = MAX_COMMENT
        chunks.append(text[:cut])
        text = text[cut:]
    return chunks


def create_batch(repo_id, label_ids, prs):
    var_defs = ["$repo:ID!"]
    fields = []
    variables = {"repo": repo_id}
    for idx, pr in enumerate(prs):
        var_defs += [f"$t{idx}:String!", f"$b{idx}:String!", f"$l{idx}:[ID!]!"]
        fields.append(f"i{idx}:createIssue(input:{{repositoryId:$repo,title:$t{idx},body:$b{idx},labelIds:$l{idx}}}){{issue{{id number}}}}")
        variables[f"t{idx}"] = f"[Archived PR #{pr['number']}] {pr['title']}"
        variables[f"b{idx}"] = build_body(pr)
        names = [n["name"] for n in pr.get("labels", {}).get("nodes", [])] + ["archived-pr"]
        variables[f"l{idx}"] = [label_ids[n] for n in dict.fromkeys(names) if n in label_ids]
    q = "mutation(" + ",".join(var_defs) + "){" + " ".join(fields) + "}"
    data = gh_graphql(q, variables)["data"]
    return [(pr, data[f"i{idx}"]["issue"]) for idx, pr in enumerate(prs)]


def add_comments_batch(items):
    work = []
    for pr, issue in items:
        for body in discussion(pr):
            work.append((issue["id"], body))
    for start in range(0, len(work), 10):
        chunk = work[start:start+10]
        defs, fields, vars_ = [], [], {}
        for i, (issue_id, body) in enumerate(chunk):
            defs += [f"$id{i}:ID!", f"$b{i}:String!"]
            fields.append(f"c{i}:addComment(input:{{subjectId:$id{i},body:$b{i}}}){{commentEdge{{node{{id}}}}}}")
            vars_[f"id{i}"] = issue_id
            vars_[f"b{i}"] = body
        gh_graphql("mutation(" + ",".join(defs) + "){" + " ".join(fields) + "}", vars_)


def close_batch(items):
    closed = [(pr, issue) for pr, issue in items if pr.get("state") in ("CLOSED", "MERGED") or pr.get("merged")]
    if not closed:
        return
    defs, fields, vars_ = [], [], {}
    for i, (_, issue) in enumerate(closed):
        defs.append(f"$id{i}:ID!")
        fields.append(f"u{i}:updateIssue(input:{{id:$id{i},state:CLOSED}}){{issue{{id}}}}")
        vars_[f"id{i}"] = issue["id"]
    gh_graphql("mutation(" + ",".join(defs) + "){" + " ".join(fields) + "}", vars_)


def dedupe():
    _, _, issues, _ = target_snapshot()
    groups = defaultdict(list)
    for i in issues:
        m = MARKER_RE.search(i.get("body") or "")
        if m:
            groups[m.group(0)].append(i)
    dupes = []
    for items in groups.values():
        items.sort(key=lambda x: x["number"])
        dupes.extend(items[1:])
    print(f"Deduplication: {len(groups)} unique markers, {len(dupes)} duplicates")
    for start in range(0, len(dupes), 20):
        chunk = dupes[start:start+20]
        defs, fields, vars_ = [], [], {}
        for i, issue in enumerate(chunk):
            defs.append(f"$id{i}:ID!")
            fields.append(f"d{i}:deleteIssue(input:{{issueId:$id{i}}}){{clientMutationId}}")
            vars_[f"id{i}"] = issue["id"]
        try:
            gh_graphql("mutation(" + ",".join(defs) + "){" + " ".join(fields) + "}", vars_)
        except Exception:
            print("WARN: deleteIssue unavailable; duplicates remain closed/visible", file=sys.stderr)
            return False
    return True


def main():
    repo_id, label_ids, _, markers = target_snapshot()
    print(f"Target currently has {len(markers)} unique migration markers")
    pending = []
    copied = 0
    skipped = 0
    for page in source_pages():
        for pr in page:
            if marker(pr["number"]) in markers:
                skipped += 1
                continue
            pending.append(pr)
            if len(pending) == 5:
                created = create_batch(repo_id, label_ids, pending)
                add_comments_batch(created)
                close_batch(created)
                for p, _ in created:
                    markers.add(marker(p["number"]))
                copied += len(created)
                print(f"Copied PRs: {copied}; skipped existing: {skipped}")
                pending = []
    if pending:
        created = create_batch(repo_id, label_ids, pending)
        add_comments_batch(created)
        close_batch(created)
        copied += len(created)
    print(f"PR migration finished: copied={copied}, skipped={skipped}")
    dedupe()
    _, _, _, final_markers = target_snapshot()
    issue_markers = sum(1 for m in final_markers if ":issue:" in m)
    pr_markers = sum(1 for m in final_markers if ":pr:" in m)
    print(f"FINAL UNIQUE MARKERS issue={issue_markers} pr={pr_markers} total={len(final_markers)}")
    if issue_markers != 290 or pr_markers != 334:
        raise SystemExit(f"marker verification failed: issue={issue_markers}, pr={pr_markers}")

if __name__ == "__main__":
    main()
