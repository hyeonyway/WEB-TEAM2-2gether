#!/usr/bin/env bash
set -Eeuo pipefail

JWT_SECRET="${JWT_SECRET:?JWT_SECRET 환경변수가 필요합니다.}"
DB_HOST="${DB_HOST:?DB_HOST 환경변수가 필요합니다.}"
DB_PORT="${DB_PORT:?DB_PORT 환경변수가 필요합니다.}"
DB_NAME="${DB_NAME:?DB_NAME 환경변수가 필요합니다.}"
DB_USERNAME="${DB_USERNAME:?DB_USERNAME 환경변수가 필요합니다.}"
DB_PASSWORD="${DB_PASSWORD:?DB_PASSWORD 환경변수가 필요합니다.}"
DB_SCHEMA_SYNC_MODE="${DB_SCHEMA_SYNC_MODE:-reset-on-mismatch}"
DB_SCHEMA_WAIT_SECONDS="${DB_SCHEMA_WAIT_SECONDS:-60}"
DB_SNAPSHOT_DIR="${DB_SNAPSHOT_DIR:-/app/db-snapshots}"
SCHEMA_FILE="${SCHEMA_FILE:-/app/db/resources/schema.sql}"
INITIAL_DATA_DIR="${INITIAL_DATA_DIR:-/app/db/resources/required-data}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
MYSQLDUMP_BIN="${MYSQLDUMP_BIN:-mysqldump}"

if [[ ! "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "[db-startup] DB_NAME에는 영문, 숫자, 밑줄만 사용할 수 있습니다." >&2
  exit 1
fi

if [[ "$DB_SCHEMA_SYNC_MODE" != "reset-on-mismatch" && "$DB_SCHEMA_SYNC_MODE" != "validate" ]]; then
  echo "[db-startup] DB_SCHEMA_SYNC_MODE는 reset-on-mismatch 또는 validate여야 합니다." >&2
  exit 1
fi

if [[ ! "$DB_SCHEMA_WAIT_SECONDS" =~ ^[0-9]+$ ]] || (( DB_SCHEMA_WAIT_SECONDS < 1 )); then
  echo "[db-startup] DB_SCHEMA_WAIT_SECONDS는 1 이상의 정수여야 합니다." >&2
  exit 1
fi

if [[ ! -r "$SCHEMA_FILE" ]]; then
  echo "[db-startup] 스키마 파일을 읽을 수 없습니다: $SCHEMA_FILE" >&2
  exit 1
fi

export MYSQL_PWD="$DB_PASSWORD"
MYSQL=("$MYSQL_BIN" --protocol=TCP --connect-timeout=3 --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --default-character-set=utf8mb4)
MYSQLDUMP=("$MYSQLDUMP_BIN" --protocol=TCP --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --default-character-set=utf8mb4 --no-tablespaces)

wait_for_mysql() {
  local deadline=$((SECONDS + DB_SCHEMA_WAIT_SECONDS))
  echo "[db-startup] MySQL 연결을 기다립니다: ${DB_HOST}:${DB_PORT} (최대 ${DB_SCHEMA_WAIT_SECONDS}초)"

  until "${MYSQL[@]}" --execute="SELECT 1" >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
      echo "[db-startup] ${DB_SCHEMA_WAIT_SECONDS}초 내 MySQL에 연결하지 못했습니다: ${DB_HOST}:${DB_PORT}" >&2
      exit 1
    fi
    echo "[db-startup] MySQL이 아직 준비되지 않았습니다. 2초 후 다시 시도합니다."
    sleep 2
  done

  echo "[db-startup] MySQL 연결에 성공했습니다."
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

  echo "[db-startup] 기존 DB 스냅샷 생성: $snapshot_file"
  "${MYSQLDUMP[@]}" \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    --databases "$DB_NAME" | gzip -c > "$snapshot_file"

  if [[ ! -s "$snapshot_file" ]] || ! gzip -t "$snapshot_file"; then
    echo "[db-startup] 스냅샷 검증에 실패했습니다. DB 초기화를 중단합니다." >&2
    exit 1
  fi
}

reset_database() {
  local initial_data_file

  echo "[db-startup] 현재 schema.sql로 ${DB_NAME} DB를 초기화합니다."
  "${MYSQL[@]}" --execute="DROP DATABASE IF EXISTS \`${DB_NAME}\`"
  render_sql_for_database "$SCHEMA_FILE" "$DB_NAME" | "${MYSQL[@]}"

  if [[ ! -d "$INITIAL_DATA_DIR" ]]; then
    echo "[db-startup] 필수 초기 데이터 디렉터리가 없어 실행을 건너뜁니다: $INITIAL_DATA_DIR"
    return
  fi

  while IFS= read -r initial_data_file; do
    echo "[db-startup] 필수 초기 데이터를 적용합니다: $initial_data_file"
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
    echo "[db-startup] DB 스키마가 현재 schema.sql과 일치합니다."
    cleanup "$temporary_database" "$temporary_directory"
    trap - EXIT
    return
  fi

  echo "[db-startup] DB 스키마 불일치를 감지했습니다."
  diff -u "$actual_dump" "$expected_dump" || true

  if [[ "$DB_SCHEMA_SYNC_MODE" == "validate" ]]; then
    echo "[db-startup] validate 모드에서는 DB를 초기화하지 않습니다." >&2
    exit 1
  fi

  snapshot_database
  reset_database
  echo "[db-startup] DB 스키마 초기화가 완료됐습니다."
  cleanup "$temporary_database" "$temporary_directory"
  trap - EXIT
}

main() {
  wait_for_mysql
  compare_and_sync_schema
  exec "$@"
}

main "$@"
