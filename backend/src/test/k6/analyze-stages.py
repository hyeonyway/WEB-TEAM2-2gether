#!/usr/bin/env python3
"""k6 --out json 로 뽑은 raw NDJSON을 stage 구간별로 쪼개서 endpoint별 p95/p99를 낸다.
서버 프로메테우스 조회 없이 k6 클라이언트 관점 수치만 사용한다.

사용:
  python3 analyze-stages.py <raw.json> --stage-duration 120 --targets 50,100,150,200,300,400
"""
import argparse
import json
import re
import sys
from collections import defaultdict
from datetime import datetime


ANCHOR_SCENARIOS = {"bidContextReads", "bidWrites", "generalReads"}

_FRACTION_RE = re.compile(r"\.(\d+)")


def parse_time(value):
    # k6 emits arbitrary-precision fractional seconds (e.g. 4 digits), but
    # Python's fromisoformat only accepts exactly 3 or 6 - normalize to 6.
    def pad(match):
        return "." + match.group(1)[:6].ljust(6, "0")
    return datetime.fromisoformat(_FRACTION_RE.sub(pad, value, count=1)).timestamp()


def percentile(sorted_values, pct):
    if not sorted_values:
        return 0.0
    k = (len(sorted_values) - 1) * (pct / 100)
    f = int(k)
    c = min(f + 1, len(sorted_values) - 1)
    if f == c:
        return sorted_values[f]
    return sorted_values[f] + (sorted_values[c] - sorted_values[f]) * (k - f)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("raw_json")
    parser.add_argument("--stage-duration", type=float, required=True, help="스테이지 길이(초)")
    parser.add_argument("--targets", default="", help="스테이지별 QPS 라벨, 콤마구분 (표시용)")
    args = parser.parse_args()

    targets = [t.strip() for t in args.targets.split(",") if t.strip()]

    points = []
    with open(args.raw_json) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                d = json.loads(line)
            except json.JSONDecodeError:
                continue
            if d.get("type") != "Point" or d.get("metric") != "http_req_duration":
                continue
            data = d["data"]
            tags = data.get("tags", {})
            points.append((parse_time(data["time"]), data["value"], tags))

    if not points:
        print("http_req_duration 포인트가 없다. raw json 경로/내용 확인.", file=sys.stderr)
        sys.exit(1)

    anchor_times = [t for t, _, tags in points if tags.get("scenario") in ANCHOR_SCENARIOS]
    if not anchor_times:
        print("bidContextReads/bidWrites/generalReads 태그가 붙은 포인트가 없다.", file=sys.stderr)
        sys.exit(1)
    anchor = min(anchor_times)

    num_stages = len(targets) if targets else None
    buckets = defaultdict(list)  # (stage_idx, name) -> [duration,...]
    overall = defaultdict(list)  # name -> [duration,...]
    max_stage_seen = 0

    for t, value, tags in points:
        name = tags.get("name", "?")
        if tags.get("scenario") not in ANCHOR_SCENARIOS:
            continue  # SSE 연결 등은 stage 개념이 없어 제외
        stage_idx = int((t - anchor) // args.stage_duration)
        if stage_idx < 0:
            stage_idx = 0
        if num_stages is not None and stage_idx >= num_stages:
            stage_idx = num_stages - 1
        max_stage_seen = max(max_stage_seen, stage_idx)
        buckets[(stage_idx, name)].append(value)
        overall[name].append(value)

    n_stages = num_stages if num_stages is not None else max_stage_seen + 1

    print(f"anchor(stage0 시작) = {datetime.fromtimestamp(anchor).isoformat()}")
    print(f"stage_duration = {args.stage_duration:.0f}s, stages = {n_stages}")
    print()

    for stage_idx in range(n_stages):
        label = targets[stage_idx] if stage_idx < len(targets) else "?"
        names = sorted({name for (s, name) in buckets if s == stage_idx})
        if not names:
            continue
        print(f"=== stage {stage_idx} (target QPS={label}) ===")
        print(f"{'endpoint':<55} {'count':>7} {'mean':>8} {'p95':>8} {'p99':>8} {'max':>8}")
        for name in names:
            values = sorted(buckets[(stage_idx, name)])
            mean = sum(values) / len(values)
            print(f"{name:<55} {len(values):>7} {mean:>8.1f} {percentile(values, 95):>8.1f} "
                  f"{percentile(values, 99):>8.1f} {values[-1]:>8.1f}")
        print()

    print("=== 전체(all stages) ===")
    print(f"{'endpoint':<55} {'count':>7} {'mean':>8} {'p95':>8} {'p99':>8} {'max':>8}")
    for name in sorted(overall):
        values = sorted(overall[name])
        mean = sum(values) / len(values)
        print(f"{name:<55} {len(values):>7} {mean:>8.1f} {percentile(values, 95):>8.1f} "
              f"{percentile(values, 99):>8.1f} {values[-1]:>8.1f}")


if __name__ == "__main__":
    main()
