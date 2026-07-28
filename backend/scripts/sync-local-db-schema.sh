#!/usr/bin/env bash
set -Eeuo pipefail

# 로컬 개발 DB 스키마를 프로젝트의 schema.sql 기준으로 맞춰주는 스크립트.
# 서버 실행(scripts/start-server.sh)과 별개로, 개발자가 필요할 때 직접 실행한다.
#
# 사용법:
#   backend/scripts/sync-local-db-schema.sh
#
# backend/.env가 있으면 자동으로 읽어서 DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD를
# 채운다. 커맨드라인에서 직접 넘긴 환경변수가 있으면 그게 우선한다.
#   DB_PASSWORD=xxx backend/scripts/sync-local-db-schema.sh
#   DB_SCHEMA_SYNC_MODE=validate 로 주면 초기화 없이 불일치만 확인하고 종료한다.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# backend/.env가 있으면 값을 채워 넣는다. 이미 환경변수로 넘어온 값(예: 커맨드라인에서
# DB_PASSWORD=... 로 직접 지정)이 있으면 그걸 우선하고 .env 값으로 덮어쓰지 않는다.
ENV_FILE="${ENV_FILE:-${BACKEND_DIR}/.env}"
if [[ -f "$ENV_FILE" ]]; then
  echo "[local-db-sync] ${ENV_FILE} 에서 접속 정보를 불러옵니다."
  while IFS='=' read -r env_key env_value || [[ -n "$env_key" ]]; do
    [[ -z "$env_key" || "$env_key" == \#* ]] && continue
    if [[ -z "${!env_key+x}" ]]; then
      export "$env_key=$env_value"
    fi
  done < "$ENV_FILE"
fi

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-dbidding}"
DB_USERNAME="${DB_USERNAME:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
DB_SCHEMA_SYNC_MODE="${DB_SCHEMA_SYNC_MODE:-reset-on-mismatch}"
DB_SCHEMA_WAIT_SECONDS="${DB_SCHEMA_WAIT_SECONDS:-10}"
DB_SNAPSHOT_DIR="${DB_SNAPSHOT_DIR:-${BACKEND_DIR}/db-snapshots}"
SCHEMA_FILE="${SCHEMA_FILE:-${BACKEND_DIR}/src/main/resources/schema.sql}"
INITIAL_DATA_DIR="${INITIAL_DATA_DIR:-${BACKEND_DIR}/src/main/resources/required-data}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
MYSQLDUMP_BIN="${MYSQLDUMP_BIN:-mysqldump}"

if [[ ! "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "[local-db-sync] DB_NAME에는 영문, 숫자, 밑줄만 사용할 수 있습니다." >&2
  exit 1
fi

if [[ "$DB_SCHEMA_SYNC_MODE" != "reset-on-mismatch" && "$DB_SCHEMA_SYNC_MODE" != "validate" ]]; then
  echo "[local-db-sync] DB_SCHEMA_SYNC_MODE는 reset-on-mismatch 또는 validate여야 합니다." >&2
  exit 1
fi

if [[ ! -r "$SCHEMA_FILE" ]]; then
  echo "[local-db-sync] 스키마 파일을 읽을 수 없습니다: $SCHEMA_FILE" >&2
  exit 1
fi

export MYSQL_PWD="$DB_PASSWORD"
# 로컬 개발 DB(주로 localhost)는 유닉스 소켓 인증 계정만 있는 경우가 많아
# start-server.sh(컨테이너 간 통신용)와 달리 --protocol=TCP를 강제하지 않는다.
MYSQL=("$MYSQL_BIN" --connect-timeout=3 --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --default-character-set=utf8mb4)
MYSQLDUMP=("$MYSQLDUMP_BIN" --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --default-character-set=utf8mb4 --no-tablespaces)

wait_for_mysql() {
  local deadline=$((SECONDS + DB_SCHEMA_WAIT_SECONDS))
  local last_error=""
  echo "[local-db-sync] MySQL 연결을 기다립니다: ${DB_HOST}:${DB_PORT} (최대 ${DB_SCHEMA_WAIT_SECONDS}초)"

  until last_error="$("${MYSQL[@]}" --execute="SELECT 1" 2>&1 >/dev/null)"; do
    if (( SECONDS >= deadline )); then
      echo "[local-db-sync] ${DB_SCHEMA_WAIT_SECONDS}초 내 MySQL에 연결하지 못했습니다: ${DB_HOST}:${DB_PORT}" >&2
      echo "[local-db-sync] 마지막 에러: ${last_error}" >&2
      echo "[local-db-sync] 로컬 MySQL이 켜져 있는지, 접속 정보(DB_HOST/DB_PORT/DB_USERNAME/DB_PASSWORD)가 맞는지 확인하세요." >&2
      exit 1
    fi
    echo "[local-db-sync] MySQL이 아직 준비되지 않았습니다. 2초 후 다시 시도합니다. (${last_error})"
    sleep 2
  done

  echo "[local-db-sync] MySQL 연결에 성공했습니다."
}

render_sql_for_database() {
  local source_file="$1"
  local target_database="$2"
  sed \
    -e "s/CREATE DATABASE IF NOT EXISTS dbidding/CREATE DATABASE IF NOT EXISTS ${target_database}/g" \
    -e "s/^USE dbidding;$/USE ${target_database};/g" \
    -e "s/^USE \`dbidding\`;$/USE \`${target_database}\`;/g" \
    "$source_file"
}

normalize_schema_dump() {
  sed \
    -e '/^--/d' \
    -e '/^\/\*!/d' \
    -e 's/AUTO_INCREMENT=[0-9][0-9]* //g' \
    -e '/^[[:space:]]*$/d'
}

snapshot_database() {
  local timestamp snapshot_file
  timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
  snapshot_file="${DB_SNAPSHOT_DIR}/${DB_NAME}-${timestamp}.sql.gz"
  mkdir -p "$DB_SNAPSHOT_DIR"

  echo "[local-db-sync] 초기화 전 스냅샷 생성: $snapshot_file"
  "${MYSQLDUMP[@]}" \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    --databases "$DB_NAME" | gzip -c > "$snapshot_file"

  if [[ ! -s "$snapshot_file" ]] || ! gzip -t "$snapshot_file"; then
    echo "[local-db-sync] 스냅샷 검증에 실패했습니다. DB 초기화를 중단합니다." >&2
    exit 1
  fi
}

reset_database() {
  local initial_data_file

  echo "[local-db-sync] 현재 schema.sql로 ${DB_NAME} DB를 다시 만듭니다."
  "${MYSQL[@]}" --execute="DROP DATABASE IF EXISTS \`${DB_NAME}\`"
  render_sql_for_database "$SCHEMA_FILE" "$DB_NAME" | "${MYSQL[@]}"

  if [[ ! -d "$INITIAL_DATA_DIR" ]]; then
    echo "[local-db-sync] 필수 초기 데이터 디렉터리가 없어 실행을 건너뜁니다: $INITIAL_DATA_DIR"
    return
  fi

  while IFS= read -r initial_data_file; do
    echo "[local-db-sync] 필수 초기 데이터를 적용합니다: $initial_data_file"
    render_sql_for_database "$initial_data_file" "$DB_NAME" | "${MYSQL[@]}" "$DB_NAME"
  done < <(find "$INITIAL_DATA_DIR" -maxdepth 1 -type f -name '*.sql' -size +0c | LC_ALL=C sort)
}

compare_and_sync_schema() {
  local temporary_database temporary_directory actual_dump expected_dump
  temporary_database="${DB_NAME}_schema_check_$$"
  temporary_directory="$(mktemp -d)"
  actual_dump="${temporary_directory}/actual.sql"
  expected_dump="${temporary_directory}/expected.sql"

  cleanup() {
    local database_to_drop="$1"
    local directory_to_remove="$2"
    "${MYSQL[@]}" --execute="DROP DATABASE IF EXISTS \`${database_to_drop}\`" >/dev/null 2>&1 || true
    rm -rf "$directory_to_remove"
  }
  trap "cleanup '$temporary_database' '$temporary_directory'" EXIT

  "${MYSQL[@]}" --execute="CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
  render_sql_for_database "$SCHEMA_FILE" "$temporary_database" | "${MYSQL[@]}"

  "${MYSQLDUMP[@]}" --no-data --skip-comments --compact "$DB_NAME" | normalize_schema_dump > "$actual_dump"
  "${MYSQLDUMP[@]}" --no-data --skip-comments --compact "$temporary_database" |
    sed "s/\`${temporary_database}\`/\`${DB_NAME}\`/g" |
    normalize_schema_dump > "$expected_dump"

  if cmp -s "$actual_dump" "$expected_dump"; then
    echo "[local-db-sync] DB 스키마가 현재 schema.sql과 일치합니다. 변경할 게 없습니다."
    cleanup "$temporary_database" "$temporary_directory"
    trap - EXIT
    return
  fi

  echo "[local-db-sync] DB 스키마 불일치를 감지했습니다."
  diff -u "$actual_dump" "$expected_dump" || true

  if [[ "$DB_SCHEMA_SYNC_MODE" == "validate" ]]; then
    echo "[local-db-sync] validate 모드라 DB를 초기화하지 않고 종료합니다." >&2
    cleanup "$temporary_database" "$temporary_directory"
    trap - EXIT
    exit 1
  fi

  snapshot_database
  reset_database
  echo "[local-db-sync] DB 스키마를 schema.sql 기준으로 다시 맞췄습니다."
  cleanup "$temporary_database" "$temporary_directory"
  trap - EXIT
}

wait_for_mysql
compare_and_sync_schema