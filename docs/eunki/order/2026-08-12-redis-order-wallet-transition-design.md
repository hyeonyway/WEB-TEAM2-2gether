# Redis 주문 지갑 상태 전이 설계

## 목적

주문 확정과 취소가 유발하는 지갑 정산·환불을 MySQL 행 잠금 승인 경로에서 분리한다. 주문 전체를 Redis-first 조회 모델로 바꾸는 것이 아니라, 다음 입찰의 가용 잔액에 영향을 주는 상태만 Redis Lua에서 즉시 전이한다.

## 범위

- 구매 확정: `PENDING_CONFIRM -> COMPLETED`, 판매자 정산
- 구매자 취소: `PENDING_CONFIRM -> CANCELLED`, 구매자 환불
- 판매자 취소: `PENDING_CONFIRM -> CANCELLED`, 구매자 환불
- Redis 승인 결과를 단일 timeline Stream으로 발행하고 MySQL 주문·지갑 projection을 비동기로 처리

주문 상세·배송·목록 API의 전체 Redis 전환, 주문 API 형식 변경, SSE 신규 추가는 범위에서 제외한다.

## Redis 상태와 원자 전이

기존 즉시 낙찰이 생성한 `order:state:{auctionId}`를 명령의 원본 상태로 사용한다. Lua는 주문 상태, 대상 지갑 Hash, 멱등 결과 key, `event:timeline`을 한 번의 EVAL에서 처리한다.

```text
POST /orders/{orderId}/confirm
  Redis Lua
    order state 및 참여자 검증
    PENDING_CONFIRM -> COMPLETED
    seller wallet availableBalance 증가, walletVersion 증가
    XADD order.completed.v1

POST /orders/{orderId}/cancel | /seller-cancel
  Redis Lua
    order state 및 요청자 역할 검증
    PENDING_CONFIRM -> CANCELLED
    buyer wallet availableBalance 증가, walletVersion 증가
    XADD order.cancelled.v1
```

응답 유실 후 같은 HTTP 요청이 재시도되는 경우를 위해 명령 종류와 `orderId`로 결정적 멱등 키를 만든다. 동일 명령은 저장된 결과를 반환하고 Stream 이벤트를 중복 생성하지 않는다. 다른 역할의 취소는 별도 키를 쓰더라도 상태 검증에서 거부된다. 클라이언트 멱등 키 헤더 계약은 프론트·백엔드 API 계약 이슈에서 별도로 정한다.

## Stream 및 MySQL projection

이벤트에는 `eventId`, `orderId`, `auctionId`, 주문 version, 명령자와 역할, 주문 상태, 지갑 사용자·walletVersion·변경 후 잔액, 발생 시각을 담는다.

단일 consumer는 inbox `streamId` 멱등성을 먼저 기록한 뒤 하나의 MySQL 트랜잭션에서 다음을 처리한다.

- orders 상태를 전이한다.
- wallets projection을 `projection_version < walletVersion` 조건으로 갱신한다.
- point_records를 eventId 멱등성으로 기록한다.
- 기존 주문 완료·취소 도메인 이벤트를 커밋 후 발행한다.

DB projection 실패 시 Stream ACK를 하지 않아 재시도한다. Redis에서 이미 승인된 주문·지갑 상태는 되돌리지 않는다.

## 기존 DB 프로필

`!redis` 프로필은 기존 `OrderService`의 행 잠금 트랜잭션을 유지한다. `redis` 프로필에서만 명령 executor를 대체한다. Redis state가 없거나 projection이 따라잡지 못한 상태라면 MySQL로 조용히 fallback하지 않고 복구 필요 오류를 반환해 오래된 주문 상태로 잘못 승인하지 않는다.

## 검증

- 확정·양쪽 취소의 상태/권한/지갑 변화/Stream 발행 원자성
- 중복 명령은 단일 Stream 이벤트와 동일 응답
- 완료 또는 취소된 주문의 추가 명령 거부
- consumer projection의 orders·wallets·point_records 멱등성
- DB projection 실패 뒤 재전달 처리
