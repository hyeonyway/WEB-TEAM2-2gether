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

관리자 화면은 `/admin/stream-recovery`이며, API는
`GET /api/admin/auction-stream/recovery/status`다.

- `/api/auth/me`이 현재 계정의 `role`을 반환한다.
- API는 DB의 실제 `AccountRole.ADMIN`만 허용한다.
- pause 상태, `PENDING`/`ERROR` 건수, 가장 이른 미완료 Stream ID와 실패 메시지를 제공한다.
- 로컬 필수 시드에는 `admin@dbidding.com` ADMIN 계정이 포함된다.

## 후속 복구 실행 설계

실제 replay는 관리자 화면의 dry-run과 명시적 확인을 거친 백그라운드 작업으로 제공한다.

1. 가장 이른 미완료 inbox 이벤트부터 DB inbox ID 순서로 범위를 고정한다.
2. consumer leader lock 아래에서 inbox payload를 순서대로 다시 읽는다.
3. 기존 projection service로 MySQL에만 재적용하고, 성공한 inbox를 `PROCESSED`로 변경한다.
4. 한 이벤트라도 실패하면 즉시 중단하고 실행자·범위·결과·실패 사유를 감사 이력에 남긴다.
5. 실패한 이벤트는 `ERROR`로 남기며, 뒤 이벤트는 `PENDING`으로 보류된 순서대로 재처리한다.

이 작업은 Redis 상태를 다시 승인하거나 Stream group cursor를 되감지 않는다.
