# Redis 승인 경계와 Stream Projection 아키텍처

## 목적

이 구조의 목적은 모든 도메인을 Redis-first로 이전하는 것이 아니다. 특정 경매의 입찰과 그 입찰 가능 잔액에 대한 MySQL 행 잠금 대기가 요청 처리 지연으로 전파되는 것을 줄이는 것이다.

Redis는 짧은 Lua 상태 전이로 실시간 승인을 처리하고, Redis Stream은 MySQL이 처리량 변동이나 일시 장애를 흡수할 수 있는 durable buffer 역할을 한다. MySQL은 영구 이력, 감사, 통계, 복구와 비동기 projection을 유지한다.

## 승인 경계

다음 입찰의 승인 조건에 직접 영향을 주는 데이터만 Redis의 최신 상태로 유지한다.

| 영역 | Redis 승인 상태 | MySQL projection |
| --- | --- | --- |
| 경매 | 상태, 현재가, 최고 입찰자, hold 금액, sequence, 마감 시각 | auctions, bids, 이미지 이력 |
| 지갑 | available/frozen 잔액, walletVersion, 경매별 hold | wallets, wallet_holds, point_records |
| 주문으로 인한 금액 이동 | 확정/취소 주문 상태, 정산·환불 대상 지갑 | orders, 지갑 원장과 현재 projection |
| 주문의 배송·상세·부가 기능 | 승인 조건과 무관하므로 이전 대상 아님 | orders 기반 기존 기능 유지 |

## Redis 키

```text
auction:state:{auctionId}             활성 경매 승인 상태
auction:bidder:{auctionId}:{userId}   내 입찰 상태
auction:recent-bids:{auctionId}       최근 50건 타임라인
auction:active:by-close-time          활성 경매 마감 인덱스

wallet:balance:{userId}               availableBalance, frozenBalance, walletVersion
wallet:hold:{auctionId}:{userId}      경매별 예치금

order:state:{auctionId}               주문 상태, 참여자, 가격, orderVersion, projectionStatus
order:state:by-order-id:{orderId}     MySQL orderId -> auctionId 인덱스
order:state:buyer:{userId}            구매 주문 인덱스
order:state:seller:{userId}           판매 주문 인덱스

event:timeline                        전역 단일 Redis Stream
```

단일 Redis 인스턴스와 AOF `appendfsync always`를 전제로 한다. Redis Cluster로 확장할 경우 Lua에서 함께 접근하는 키의 hash tag 설계를 별도로 수행한다.

## Lua 승인 흐름

### 입찰

`bid-accept.lua`는 경매 상태·입찰가·판매자/최고입찰자·마감·가용 잔액을 검증한 뒤 다음을 한 번에 처리한다.

1. 신규 최고 입찰자 hold 및 이전 최고 입찰자 release
2. 경매 가격·최고 입찰자·sequence·마감 상태 전이
3. 최근 입찰 타임라인 기록
4. `bid.accepted.v1` 또는 `auction.buy-now.v1` Stream 발행
5. 즉시 낙찰이면 pending 주문 state 생성

### 일반 지갑 명령

충전, 환불, 판매 정산, 주문 취소 환불은 `wallet-transition.lua`에서 walletVersion 증가와 Stream 발행을 원자적으로 처리한다.

### 주문이 유발하는 지갑 명령

`order-wallet-transition.lua`는 기존 주문 API의 `orderId`를 그대로 받는다. 서버는 명령 종류와 orderId로 결정적 멱등 키를 만들며, Lua가 다음을 한 번에 처리한다.

- 구매 확정: `PENDING_CONFIRM -> COMPLETED`, 판매자 정산
- 구매자 취소: `PENDING_CONFIRM -> CANCELLED`, 구매자 환불
- 판매자 취소: `PENDING_CONFIRM -> CANCELLED`, 구매자 환불
- orderVersion/walletVersion 증가와 `order.completed.v1` 또는 `order.cancelled.v1` 발행

같은 요청이 응답 유실 후 재시도되면 저장된 결과를 반환하고 Stream 이벤트를 중복 생성하지 않는다. 이미 완료·취소된 주문 또는 권한 없는 요청은 상태·지갑·Stream을 변경하지 않는다.

즉시 낙찰 직후 MySQL orderId가 아직 없는 pending 주문은 기존처럼 확정·취소 대상이 아니다. MySQL projection이 orderId를 Redis state에 연결한 뒤 주문 명령을 허용한다.

## 단일 consumer와 MySQL projection

`event:timeline`은 전역 단일 consumer가 순서대로 처리한다. 각 이벤트는 inbox의 streamId로 중복 수신을 기록하고, 하나의 MySQL 트랜잭션에서 projection한다.

- 경매 이벤트: auctions, bids, wallet hold/capture, 즉시 낙찰 order 생성
- 지갑 이벤트: point_records eventId 멱등 기록, walletVersion 조건부 wallets 갱신
- 주문 이벤트: orders 상태 전이, 지갑 원장·wallets projection, 기존 주문 완료/취소 후속 이벤트 발행

projection 성공 뒤 Redis 주문 state는 `projectionStatus=PROJECTED`와 orderId/상태를 갱신한다. 실패 상태는 Redis 승인 자체를 취소하지 않으며, 운영 복구 절차에서 inbox와 Stream을 기준으로 재처리한다.

## 상태 복원

Redis state miss가 발생해도 과거 MySQL projection으로 최신 Redis 승인을 덮어쓰면 안 된다. 따라서 경매·지갑·주문 시더는 다음 규칙을 따른다.

1. 기존 Redis key가 있으면 덮어쓰지 않는다.
2. 같은 key의 동시 miss는 Single-Flight로 합친다.
3. Stream 최신 이벤트가 PROCESSED이고 PENDING/ERROR inbox가 없을 때만 MySQL projection에서 조건부 seed한다.
4. catch-up이 확인되지 않으면 MySQL fallback 승인 대신 복구 필요 오류를 반환한다.

기동 시에는 마감 임박·최근 활성 경매만 warm-up한다. 전체 Redis 복구는 Stream/AOF replay와 MySQL projection의 catch-up을 먼저 확인한 뒤 수행한다.

## 운영 제약

- Redis는 MySQL 처리량을 늘리지 않는다. 지속 유입량이 consumer 처리량을 넘으면 Stream backlog가 누적된다.
- Stream lag, PEL, inbox PENDING/ERROR, Redis 메모리, AOF 크기를 모니터링한다.
- backlog가 허용치를 넘으면 새 입찰·지갑 승인을 제한하는 정책이 필요하다.
- 활성 경매 ZSET은 종료/취소/유찰 상태만 보수적으로 정리한다. OPEN/ENDING을 제거하면 마감 재시도가 불가능해진다.
- 단일 consumer 처리량을 측정한 뒤에만 shard 병렬화를 검토한다. 병렬화 시 지갑 version 순서와 경매 순서를 별도로 보장해야 한다.
