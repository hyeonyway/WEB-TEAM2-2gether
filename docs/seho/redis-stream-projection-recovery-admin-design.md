# Redis Stream Projection 복구 관리자 설계

## 배경

Redis 승인 경로는 상태 전이와 `event:timeline` 기록을 먼저 완료하고, 단일 consumer가 MySQL
projection을 비동기로 반영한다. 따라서 DB projection 실패는 Redis 승인 자체를 되돌리지 않고,
원인 파악과 안전한 재처리가 가능해야 한다.

## 실패 처리

`AuctionBidStreamConsumer`는 Stream 원본 payload를 JSON으로 inbox에 기록한 뒤 ACK·삭제한다.
실제 projection은 inbox의 가장 이른 `PENDING` 행만 조회해 수행한다.

- `TransientDataAccessException`, `RecoverableDataAccessException`, DB transaction 생성 실패는
  최대 3회(1초, 2초, 4초) 재시도한다.
- 이벤트 계약·버전·도메인 오류 및 재시도 소진은 inbox를 `ERROR`로 전환한다.
- terminal 오류는 inbox를 `ERROR`로 기록한 뒤 Stream entry를 ACK하고 `XDEL`한다.
  이후 이벤트는 inbox에 `PENDING`으로만 보존하고, 선행 오류가 해소될 때까지 projection하지 않는다.
- inbox에는 `attempt_count`, `last_attempt_at`, 실패 메시지를 보존한다.

Stream은 단기 전달 버퍼다. inbox 기록이 성공한 entry는 ACK 직후 `XDEL`해 장기 보관하지 않는다.
오류 이벤트의 원본·원인은 MySQL inbox가 운영 추적·재처리 근거가 된다.

## 관리자 상태 화면

관리자 화면은 `/admin`이며, 기존 `/admin/stream-recovery`는 이 경로로 이동한다. API는
`GET /api/admin/auction-stream/recovery/status`다. `POST /api/admin/auction-stream/recovery/replay`는
ADMIN이 원인 조치 후 첫 `ERROR`를 `PENDING`으로 되돌린다. 이후 worker는 Redis Stream을 다시 읽지 않고
DB inbox ID 순서로 오류 이벤트와 후속 PENDING을 재투영한다. 화면은 대상 Stream ID와 재실패 시 중단됨을
명시하는 주의 모달을 거친 뒤에만 이 요청을 보낸다.

- `/api/auth/me`이 현재 계정의 `role`을 반환한다.
- API는 DB의 실제 `AccountRole.ADMIN`만 허용한다.
- `PENDING`/`ERROR` 건수, 가장 이른 미완료 Stream ID와 실패 메시지를 제공한다.
- 로컬 필수 시드에는 `admin@dbidding.com` ADMIN 계정이 포함된다.

## 후속 복구 실행 설계

복구 요청은 명시적 확인 뒤 첫 ERROR만 PENDING으로 되돌린다. 활성 consumer가 다음 poll에서
DB inbox를 읽어 처리하므로 HTTP 요청이 projection을 직접 실행하지 않는다.

1. 오류 이벤트가 다시 실패하면 즉시 ERROR가 되고, 이후 이벤트는 PENDING으로 남는다.
2. 성공하면 같은 worker가 다음 inbox 행을 이어서 처리한다.
3. 중복 실행해도 ERROR가 없으면 재처리하지 않고 `accepted=false`를 반환한다.

이 작업은 Redis 상태를 다시 승인하거나 Stream group cursor를 되감지 않는다.
