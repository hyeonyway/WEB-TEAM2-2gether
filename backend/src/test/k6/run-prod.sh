#!/usr/bin/env bash
# prod(api.dbidding.shop)에 시나리오①/②를 돌리기 위한 러너.
# 스크립트 자체(pure-throughput.js/hot-auction-pattern.js)는 이미 env var로
# 완전히 파라미터화돼 있어 코드 수정 없이 BASE_URL만 바꾸면 prod로 돈다.
# 이 래퍼는 실수(오타 URL, 결과 파일 이름 규칙 등)만 줄여준다.
#
# 5-redis-baseline-comparison.md 기준선 절차 참고.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K6_BIN="$SCRIPT_DIR/sse/k6-sse"
RESULT_DIR="${K6_RESULT_DIR:-$SCRIPT_DIR/results}"
BASE_URL="${BASE_URL:-https://api.dbidding.shop}"
TODAY="$(date +%Y%m%d)"

usage() {
  cat <<'EOF'
Usage:
  run-prod.sh pure-throughput <250|500|1000> [추가 -e KEY=VALUE ...]
  run-prod.sh hot-auction-pattern <AUCTION_IDS(콤마구분,정확히200개)> [<HOT_AUCTION_IDS(콤마구분)>] [추가 -e KEY=VALUE ...]

환경변수:
  BASE_URL           기본값 https://api.dbidding.shop
  LOAD_TEST_PASSWORD 기본값 K6LoadTest123! (k6-userNNNNN 계정 공통 비밀번호)
  K6_RESULT_DIR       기본값 ./results (baseline-pre-redis-<시나리오>-<날짜>.json 저장)

주의: hot-auction-pattern은 AUCTION_IDS가 서로 다른 OPEN/ENDING 경매 ID
정확히 200개여야 한다(스크립트 자체가 검증해서 실패함). 2026-08-11 기준
prod에는 OPEN 60 + ENDING 14 = 74개뿐이라 부족하다 — 200개 채우기 전엔
이 시나리오 실행 불가. pure-throughput은 이 제약이 없다(진행 중 경매
1개 이상이면 됨, AUCTION_IDS 안 주면 자동 조회).
EOF
  exit 1
}

[ -x "$K6_BIN" ] || { echo "k6-sse 바이너리 없음: $K6_BIN (backend/src/test/k6/sse/README.md 참고해서 빌드)"; exit 1; }
mkdir -p "$RESULT_DIR"

scenario="${1:-}"
case "$scenario" in
  pure-throughput)
    tier="${2:-}"
    [[ "$tier" =~ ^(250|500|1000)$ ]] || usage
    shift 2
    result_file="$RESULT_DIR/baseline-pre-redis-pure-throughput-sse${tier}-${TODAY}.json"
    echo "== pure-throughput (SSE_VUS=$tier) -> $BASE_URL =="
    "$K6_BIN" run \
      -e BASE_URL="$BASE_URL" \
      -e SSE_VUS="$tier" \
      -e LOAD_TEST_PASSWORD="${LOAD_TEST_PASSWORD:-K6LoadTest123!}" \
      -e K6_RESULT_FILE="$result_file" \
      "$@" \
      "$SCRIPT_DIR/scenarios/pure-throughput.js"
    ;;
  hot-auction-pattern)
    auction_ids="${2:-}"
    hot_ids="${3:-}"
    [ -n "$auction_ids" ] || usage
    shift 2
    [ -n "${1:-}" ] && [[ "$1" != -e* ]] && { hot_ids="$1"; shift; }
    result_file="$RESULT_DIR/baseline-pre-redis-hot-auction-pattern-${TODAY}.json"
    echo "== hot-auction-pattern -> $BASE_URL =="
    "$K6_BIN" run \
      -e BASE_URL="$BASE_URL" \
      -e AUCTION_IDS="$auction_ids" \
      ${hot_ids:+-e HOT_AUCTION_IDS="$hot_ids"} \
      -e LOAD_TEST_PASSWORD="${LOAD_TEST_PASSWORD:-K6LoadTest123!}" \
      -e K6_RESULT_FILE="$result_file" \
      "$@" \
      "$SCRIPT_DIR/scenarios/hot-auction-pattern.js"
    ;;
  *)
    usage
    ;;
esac

echo "결과 저장: $result_file"
