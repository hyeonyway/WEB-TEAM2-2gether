#!/usr/bin/env bash
set -Eeuo pipefail

REDIS_HOST="${REDIS_HOST:?REDIS_HOST 환경변수가 필요합니다.}"
REDIS_PORT="${REDIS_PORT:?REDIS_PORT 환경변수가 필요합니다.}"
REDIS_USERNAME="${REDIS_USERNAME:?REDIS_USERNAME 환경변수가 필요합니다.}"
REDIS_PASSWORD="${REDIS_PASSWORD:?REDIS_PASSWORD 환경변수가 필요합니다.}"
REDIS_CLI="${REDIS_CLI:-redis-cli}"
REDIS_WAIT_SECONDS="${REDIS_WAIT_SECONDS:-60}"

redis_cli_args=("$REDIS_CLI" --no-auth-warning -h "$REDIS_HOST" -p "$REDIS_PORT" --user "$REDIS_USERNAME")
if [[ "${REDIS_SSL_ENABLED:-false}" == "true" ]]; then
  redis_cli_args+=(--tls)
fi

deadline=$((SECONDS + REDIS_WAIT_SECONDS))
echo "[startup] Redis 연결을 기다립니다: ${REDIS_HOST}:${REDIS_PORT}"
until REDISCLI_AUTH="$REDIS_PASSWORD" "${redis_cli_args[@]}" PING >/dev/null 2>&1; do
  if (( SECONDS >= deadline )); then
    echo "[startup] ${REDIS_WAIT_SECONDS}초 내 Redis에 연결하지 못했습니다." >&2
    exit 1
  fi
  sleep 2
done

exec "$@"
