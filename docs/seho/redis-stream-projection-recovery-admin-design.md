# Redis Stream Projection 복구 관리자 설계

## 배경

Redis 승인 경로는 상태 전이와 `event:timeline` 기록을 먼저 완료하고, 단일 consumer가 MySQL
projection을 비동기로 반영한다. 따라서 DB projection 실패는 Redis 승인 자체를 되돌리지 않고,
원인 파악과 안전한 재처리가 가능해야 한다.

## 실패 처리

`AuctionBidStreamConsumer`는 이벤트를 inbox에 기록한 뒤 projection을 수행한다.

- `TransientDataAccessException`, `RecoverableDataAccessException`, DB transaction 생성 실패는
  최대 3회(1초, 2초, 4초) 재시도한다.
- 이벤트 계약·버전·도메인 오류 및 재시도 소진은 inbox를 `ERROR`로 전환한다.
- terminal 오류는 Stream entry를 ACK하고 Redis의 `event:timeline:paused`를 설정한다.
  이후 consumer는 새 이벤트를 projection하지 않는다.
- inbox에는 `attempt_count`, `last_attempt_at`, 실패 메시지를 보존한다.

ACK는 Stream 원본 삭제가 아니다. `XDEL`/`XTRIM`은 replay 근거를 잃게 하므로 이 설계 범위에서
사용하지 않는다.

## 관리자 상태 화면

관리자 화면은 `/admin/stream-recovery`이며, API는
`GET /api/admin/auction-stream/recovery/status`다.

- `/api/auth/me`이 현재 계정의 `role`을 반환한다.
- API는 DB의 실제 `AccountRole.ADMIN`만 허용한다.
- pause 상태, `PENDING`/`ERROR` 건수, 가장 이른 미완료 Stream ID와 실패 메시지를 제공한다.
- 로컬 필수 시드에는 `admin@dbidding.com` ADMIN 계정이 포함된다.

## 후속 복구 실행 설계

실제 replay는 관리자 화면의 dry-run과 명시적 확인을 거친 백그라운드 작업으로 제공한다.

1. 가장 이른 미완료 inbox 이벤트부터 실행 시작 시점의 마지막 Stream ID까지 범위를 고정한다.
2. pause 및 consumer leader lock 아래에서 Stream ID 순서대로 원본 payload를 다시 읽는다.
3. 기존 projection service로 MySQL에만 재적용하고, 성공한 inbox를 `PROCESSED`로 변경한다.
4. 한 이벤트라도 실패하면 즉시 중단하고 실행자·범위·결과·실패 사유를 감사 이력에 남긴다.
5. 모든 대상이 성공할 때만 pause를 해제해 실시간 consumer를 재개한다.

이 작업은 Redis 상태를 다시 승인하거나 Stream group cursor를 되감지 않는다.
