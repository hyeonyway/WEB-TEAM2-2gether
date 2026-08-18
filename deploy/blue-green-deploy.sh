#!/usr/bin/env bash
# blue-green(port-swap) 컷오버 스크립트. backend 호스트에서, 이 스크립트와 같은 디렉터리에
# docker-compose.prod.yml이 있다는 전제로 실행한다. nginx는 이 스크립트가 관리하지 않는다
# (~/nginx/에서 수동 관리) - upstream-active.inc 파일 하나만 갱신하고 reload만 시킨다.
#
# 순서: 안 떠 있는 색으로 새 이미지 기동 -> readiness 폴링 -> nginx upstream 전환 ->
# 유예 대기 -> 이전 색 정리. readiness가 제한 시간 안에 통과하지 못하면 새 컨테이너를
# 내리고 실패로 종료한다 - 기존 색은 그대로 서비스를 계속한다(자동 롤백).
#
# 설계 근거: docs/hyeonmoon/global/4-blue-green-deploy-readiness-design.md
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
# nginx는 이 스크립트/compose 파일과 별개 위치(~/nginx/)에서 수동 관리된다.
UPSTREAM_CONF="${UPSTREAM_CONF:-$HOME/nginx/conf.d/upstream-active.inc}"
READINESS_TIMEOUT_SECONDS="${READINESS_TIMEOUT_SECONDS:-300}"
READINESS_POLL_INTERVAL_SECONDS="${READINESS_POLL_INTERVAL_SECONDS:-3}"
DRAIN_GRACE_SECONDS="${DRAIN_GRACE_SECONDS:-30}"

log() { echo "[blue-green-deploy] $*"; }
fail() { echo "[blue-green-deploy] ERROR: $*" >&2; exit 1; }

port_for() {
    # nginx -> backend는 Docker 네트워크 안에서 컨테이너 이름으로 직접 붙는다 -
    # 호스트 포트 매핑(8080/8081)을 안 거치므로 컬러와 무관하게 컨테이너
    # 내부 리스닝 포트(스프링 부트 기본값 8080)를 그대로 써야 한다. 컬러별
    # 호스트 포트 8080/8081은 사람이 밖에서 curl로 색을 구분해 찌를 때만
    # 쓰는 값이라 여기(nginx upstream) 용도가 아니다.
    case "$1" in
        blue|green) echo 8080 ;;
        *) fail "알 수 없는 색: $1" ;;
    esac
}

management_port_for() {
    case "$1" in
        blue)  echo 9091 ;;
        green) echo 9092 ;;
        *) fail "알 수 없는 색: $1" ;;
    esac
}

opposite_of() {
    case "$1" in
        blue)  echo green ;;
        green) echo blue ;;
        *) fail "알 수 없는 색: $1" ;;
    esac
}

current_color() {
    if grep -q "backend-blue" "$UPSTREAM_CONF" 2>/dev/null; then
        echo blue
    elif grep -q "backend-green" "$UPSTREAM_CONF" 2>/dev/null; then
        echo green
    else
        fail "$UPSTREAM_CONF 에서 현재 색을 판별할 수 없습니다: $(cat "$UPSTREAM_CONF" 2>/dev/null || echo '<파일 없음>')"
    fi
}

wait_for_readiness() {
    local color="$1"
    local mgmt_port
    mgmt_port="$(management_port_for "$color")"
    local deadline=$((SECONDS + READINESS_TIMEOUT_SECONDS))

    log "backend-${color}(포트 ${mgmt_port})의 readiness를 최대 ${READINESS_TIMEOUT_SECONDS}초 기다립니다."
    until curl -sf "http://localhost:${mgmt_port}/actuator/health/readiness" >/dev/null 2>&1; do
        # 컨테이너가 이미 죽었으면(오늘 실제로 겪은 크래시 루프 같은 경우)
        # 타임아웃 끝까지 기다리지 않고 바로 실패 처리한다.
        if [ -z "$(docker compose -f "$COMPOSE_FILE" ps -q --status running "backend-${color}")" ]; then
            log "backend-${color} 컨테이너가 실행 중이 아닙니다 - readiness를 더 기다리지 않습니다."
            return 1
        fi
        if (( SECONDS >= deadline )); then
            return 1
        fi
        sleep "$READINESS_POLL_INTERVAL_SECONDS"
    done
    log "backend-${color} readiness 확인됨."
    return 0
}

switch_nginx_to() {
    local color="$1"
    local port
    port="$(port_for "$color")"

    log "nginx upstream을 backend-${color}(포트 ${port})로 전환합니다."
    local tmp_file
    tmp_file="$(mktemp)"
    cat > "$tmp_file" <<EOF
# blue-green-deploy.sh가 자동 생성. 수동 편집 금지 - 다음 배포 때 덮어쓴다.
server backend-${color}:${port};
EOF
    mv "$tmp_file" "$UPSTREAM_CONF"

    docker exec nginx nginx -t
    docker exec nginx nginx -s reload
    log "nginx reload 완료."
}

main() {
    local from to
    from="$(current_color)"
    to="$(opposite_of "$from")"
    log "현재 서비스 중: backend-${from}. 새로 띄울 색: backend-${to}."

    # 호출부(backend-deploy.yml)가 이미 정확한 digest를 pull해서 :latest로 태그해뒀다.
    # 여기서 다시 `compose pull`(태그 기준)을 하면 그 사이 레지스트리에 다른 이미지가
    # :latest로 올라온 경우 의도치 않은 이미지로 바뀔 수 있어 하지 않는다 - 로컬에 이미
    # 태그된 그 이미지 그대로 기동한다.
    log "backend-${to}를 기동합니다."
    docker compose -f "$COMPOSE_FILE" up -d --no-deps --force-recreate "backend-${to}"

    if ! wait_for_readiness "$to"; then
        log "backend-${to}가 제한 시간 안에 준비되지 않았습니다. 배포를 실패 처리하고 롤백합니다."
        docker compose -f "$COMPOSE_FILE" logs --tail 200 "backend-${to}" || true
        docker compose -f "$COMPOSE_FILE" stop "backend-${to}" || true
        docker compose -f "$COMPOSE_FILE" rm -f "backend-${to}" || true
        fail "backend-${to} readiness 실패 - backend-${from}가 계속 서비스 중입니다."
    fi

    switch_nginx_to "$to"

    log "이전 색(backend-${from})의 진행 중 요청이 끝나도록 ${DRAIN_GRACE_SECONDS}초 대기합니다."
    sleep "$DRAIN_GRACE_SECONDS"

    log "backend-${from}을 정지·제거합니다."
    docker compose -f "$COMPOSE_FILE" stop "backend-${from}"
    docker compose -f "$COMPOSE_FILE" rm -f "backend-${from}"
    docker image prune -f

    log "컷오버 완료: backend-${to}가 이제 트래픽을 받습니다."
}

main "$@"
