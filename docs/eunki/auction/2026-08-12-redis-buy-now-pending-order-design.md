# Redis 즉시 낙찰 주문 상태 및 비동기 Projection 설계

**관련 이슈:** #384  
**선행 이슈:** #351, #365, #383

## 목표

Redis 프로필의 즉시 낙찰은 Lua에서 경매 종료와 지갑 capture를 승인한 직후 성공 응답을 반환한다.
MySQL 주문 projection 전에는 `orders` 조회 결과가 비어 있을 수 있다. Redis 프로필에서는 이 구간만
보완하는 pending 모델이 아니라, `order:state:{auctionId}`를 주문의 최신 read model으로 사용한다.
MySQL `orders`는 영구 이력·감사·복구·기존 주문 ID API를 위한 비동기 projection이다.

## 단일 queue 처리 흐름

```text
buy-now Lua
  ├─ auction state → ENDED
  ├─ bidder hold → capture
  ├─ previous bidder hold → release
  ├─ order:state:{auctionId} 생성
  └─ XADD auction:timeline-events (auction.buy-now.v1)
                         │
               전역 단일 consumer
                         │
          MySQL bid / auction / order projection
                         │
             DB commit 후 Redis state에 orderId 연결
```

- Redis 주문 상태와 `auction.buy-now.v1`은 같은 Lua 실행에서 생성한다. 따라서 즉시 낙찰 성공 응답 뒤에는
  최신 주문 read model과 MySQL projection 원본 이벤트가 함께 존재한다.
- `auction:timeline-events`는 단일 Stream이고 leader lock을 획득한 consumer 하나가 `COUNT 1`로 순차 처리한다.
  pending order는 별도 queue나 별도 consumer를 만들지 않는다.
- MySQL 주문 생성이 커밋되면 after-commit callback으로 Redis 주문 상태에 `orderId`, `projectionStatus=PROJECTED`를 기록한다.
  Redis 주문 상태와 구매자·판매자 index는 제거하지 않는다.
- projection 오류는 Redis 주문 상태의 `projectionStatus=PROJECTION_ERROR`로 운영 복구 대상을 드러낸다. Stream은 ACK 후에도
  AOF/replay 원본으로 보존한다.

## Redis 모델과 조회 계약

`order:state:{auctionId}` hash는 `auctionId`, `orderId?`, `buyerId`, `sellerId`, `cardName`, `price`,
`status`, `projectionStatus`, `streamId`, `createdAt`을 가진다. 구매자·판매자 목록 인덱스는 각각
`order:state:buyer:{userId}`와 `order:state:seller:{userId}` Set이다.

`redis` 프로필의 주문 목록 API는 Redis 주문 상태만 `createdAt` 내림차순으로 조회한다. projection 전에는
`id=null`, `projectionStatus=PENDING`, `streamId`를, projection 후에는 MySQL `orderId`도 응답한다.
현재 주문 상세·확정·취소 API는 MySQL `orderId`를 URL 식별자로 사용하므로 projection 후에만 기존 API로
처리한다. 이 명령까지 Redis로 전환하려면 stable order ID와 `order.confirmed/cancelled` Stream 계약을
추가해야 하며 이번 이슈 범위에서 제외한다.

## 불변식

- 즉시 낙찰 승인 시 경매 종료·지갑 상태·Redis 주문 상태·timeline event는 분리되지 않는다.
- MySQL 주문이 커밋되기 전에도 구매자와 판매자는 Redis 주문 목록에서 최신 주문 상태를 조회할 수 있다.
- MySQL 주문이 생성돼도 Redis 주문 상태는 유지되고 같은 `orderId`가 연결된다.
- Redis 프로필의 주문 목록은 MySQL 목록과 병합하지 않는다.
- 같은 멱등 요청은 같은 timeline event와 pending order를 재사용하며 중복 생성하지 않는다.

## 범위 밖

- 주문 확정·취소를 Redis 명령과 Stream 이벤트로 전환하는 것
- stable order ID 기반 Redis 주문 상세 API
- 병렬 consumer 또는 주문 별도 Stream
- 자동 replay/cleanup 운영 도구와 Stream retention checkpoint
