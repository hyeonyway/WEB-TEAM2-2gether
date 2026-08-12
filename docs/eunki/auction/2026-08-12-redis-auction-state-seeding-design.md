# Redis-First 경매 생성 및 MySQL Projection 설계

**관련 이슈:** #392  
**선행 이슈:** #351, #383, #384

## 기준 역할

Redis는 경매·지갑·주문의 **현재 상태와 승인 기준**을 항상 보유한다. MySQL은 현재 상태를 먼저
만들거나 Redis를 시딩하는 기준이 아니다. Redis state 전이와 `event:timeline`의 durable event history를
단일 consumer가 순차 처리해 MySQL 이력·감사·복구 projection을 동기화한다.

```text
Client: POST /auctions
       │
       ▼
Redis Lua: ID 발급 + auction:state 생성 + XADD auction.created.v1
       │                         └─ 하나의 atomic operation
       ▼
HTTP 201 (Redis auctionId)

                 [asynchronous]
event:timeline → 전역 단일 consumer → MySQL auctions / images / inbox
```

## 경매 ID 계약

Redis Lua가 `INCR auction:sequence`으로 경매 ID를 발급한다. 이 ID는 API 응답, Redis state key,
Stream event와 MySQL `auctions.id`에 동일하게 사용한다. 따라서 MySQL `IDENTITY` 자동 생성 ID와 충돌하지
않도록 consumer projection은 명시 ID insert를 지원해야 한다.

기존 MySQL 데이터의 최대 ID보다 Redis sequence가 작으면 ID 충돌이 발생할 수 있다. Redis-first 경로를
활성화하기 전에 `auction:sequence`를 MySQL 최대 ID 이상으로 초기화하는 migration/bootstrap 절차가 필요하다.

## Redis Lua 생성 전이

Lua 입력은 판매자·카드·경매 설명·가격·마감 시각·이미지 경로·멱등 키와 request hash다. 아래를 하나의
Redis Lua 실행으로 처리한다.

1. 판매자별 멱등 key를 확인한다. 동일 request hash면 저장된 응답을 반환하고, 다르면 충돌을 반환한다.
2. `auction:sequence`을 증가시켜 `auctionId`를 발급한다.
3. `auction:state:{auctionId}`를 생성한다.
4. `auction.created.v1`을 `event:timeline`에 XADD한다.
5. event stream ID와 auctionId를 포함한 응답을 멱등 key에 저장한다.

state에는 `status`, `sellerId`, `itemId`, `cardName`, `auctionName`, `currentPrice`, `bidIncrement`,
`buyNowPrice?`, `closeTime`, `closeTimeEpochMillis`, `highestBidderId`, `highestHoldAmount`, `sequence`,
`bidCount`를 기록한다. 신규 경매의 최고 입찰자는 없으므로 빈 문자열/0/0으로 초기화한다.

## Consumer MySQL projection

단일 `event:timeline` consumer는 `auction.created.v1`을 inbox에 기록한 뒤 MySQL에 같은 `auctionId`로
`auctions`와 `images`를 생성한다. `streamId` inbox unique와 판매자+멱등 key는 replay 중복 반영을 막는다.

MySQL projection이 실패해도 Redis 승인 상태는 되돌리지 않는다. inbox에 `ERROR`를 기록하고, Stream은
AOF/replay 원본으로 보존한다. 오류를 해결한 뒤 Stream replay 또는 inbox projection 재실행으로 MySQL을
Redis history에 다시 동기화한다.

## 부트스트랩·복구

- 정상 기동에서 MySQL을 읽어 Redis 현재 state를 전수 시딩하지 않는다.
- Redis 장애 복구는 AOF와 `event:timeline` history를 우선 사용한다.
- Redis 현재 state까지 잃은 재난 복구는 Stream history를 순서대로 replay해 state를 재구성하고, MySQL은
  부족한 history를 확인하는 감사·보조 자료로만 사용한다.
- Redis sequence는 복구된 경매 ID의 최댓값 이상으로 재설정한다.

## 범위와 후속 작업

이번 이슈는 Redis-first **경매 생성**과 MySQL create projection까지 다룬다.

- 제외: 경매 수정·판매자 취소·마감 종결·주문 확정/취소의 Redis command 전환
- 제외: Redis AOF/Stream history에서 현재 경매 state를 전체 rebuild하는 운영 도구
- 후속: MySQL IDENTITY 제거 또는 명시 ID insert mapping 검증, sequence migration, replay/rebuild 통합 테스트

## 검증

- 신규 경매 생성 성공 시 Redis state와 `auction.created.v1`이 함께 존재한다.
- 같은 멱등 요청은 같은 auctionId와 하나의 event만 반환한다.
- 다른 request로 같은 멱등 key를 재사용하면 충돌한다.
- consumer는 같은 auctionId로 MySQL auction/images를 한 번만 projection한다.
- MySQL 장애는 Redis 경매 생성 승인과 즉시 Redis 조회를 막지 않고, Stream/inbox 오류로 관측된다.
