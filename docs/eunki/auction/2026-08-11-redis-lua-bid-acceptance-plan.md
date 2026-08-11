# Redis Lua 입찰 승인 구현 계획

**관련 이슈:** #351  
**연계 이슈:** #323 (Redis Stream 이벤트의 MySQL 영속화)

## 목표

기존 MySQL 동기 입찰을 `bid-db` 프로필로 유지하면서, `bid-redis-stream` 프로필에서 Redis Lua가 경매·지갑 hold 상태를 원자적으로 전이하고 같은 실행 안에서 `BidAccepted` Stream 이벤트를 생성한다.

## 범위와 경계

- #351은 Redis 승인 경로, Lua 이벤트 발행, 응답 `eventId`, 프로필 선택과 단위/통합 테스트를 담당한다.
- #323은 Stream PEL 회수와 MySQL의 입찰·지갑 이력 영속화를 담당한다. #351은 해당 consumer를 구현하거나 MySQL schema를 변경하지 않는다.
- Redis Hash는 실시간 승인 상태이며, MySQL의 append-only 지갑/입찰 이력은 영구 원장이다.
- Redis Cluster는 지원하지 않는다. 단일 Redis 인스턴스에서 Lua가 경매와 복수 지갑 키를 함께 갱신한다.

## 작업 단위

### 1. 명시 프로필과 입찰 전략 경계

**수정:** `application.yml`, `DbBidExecutor`, `RedisBidExecutor`, `RedisBidLuaConfiguration`  
**추가:** Redis 승인 결과·키 생성 전용 타입

1. 기본 실행 모드를 `bid-db`로 정하고 DB/Redis 구현체가 동시에 등록되지 않게 한다.
2. `BidExecutor` 호출부는 유지한다. Redis 구현체도 같은 반환 계약을 충족하되, DB ID 대신 결정적 `eventId`를 반환한다.
3. 기존 `redis` 단일 프로필 의존을 `bid-redis-stream`으로 교체한다.

### 2. Redis 상태 및 Stream 이벤트 계약

**추가:** `lua/bid-accept.lua`, Redis 상태 키/응답 타입, `BidAccepted` event codec

1. 다음 키를 Lua `KEYS`로 전달한다: 경매 hash, 신규 입찰자 balance/hold, 이전 최고입찰자 balance/hold, 멱등 응답 키, 대상 Stream.
2. Lua는 상태·가격·판매자·동일 최고입찰자·잔액을 검증한다.
3. 성공 시 신규 hold, 이전 hold release, 가격/최고입찰자/sequence/bidCount, walletVersion을 갱신한다.
4. 동일 Lua 실행에서 `XADD`하고 eventId, auctionId, sequence, walletVersion, 갱신 후 잔액, 요청 hash를 기록한다.
5. 같은 idempotency key와 같은 request hash는 저장한 응답을 반환하고, 다른 hash는 충돌 응답을 반환한다.

### 3. Redis 실행기와 HTTP 응답 연결

**수정:** `RedisBidExecutor`, `BidExecutionResult`, `BidResponses`, `AuctionCommandService`

1. Java가 Lua 인자와 키를 만들고 정형화된 성공/거절 코드를 도메인 예외로 변환한다.
2. API 응답의 입찰 식별자를 `eventId`로 변경한다. MySQL 생성 ID는 비동기 승인 응답에 포함하지 않는다.
3. Lua가 이미 Stream 이벤트를 만들었으므로 Redis 경로에서는 기존 Spring 이벤트를 중복 발행하지 않는다.

### 4. 검증

**추가/수정:** Redis executor/unit tests, Redis Lua Testcontainers integration tests, profile tests

1. 프로필별로 정확히 하나의 `BidExecutor`가 등록되는지 검증한다.
2. Lua 성공은 경매/두 지갑/hold/sequence와 Stream entry를 함께 변경하는지 검증한다.
3. 잔액 부족·낮은 가격·종료 경매는 어떤 키나 Stream도 변경하지 않는지 검증한다.
4. 멱등 재시도는 같은 eventId와 단 하나의 Stream entry를 반환하고, 다른 가격 재사용은 충돌하는지 검증한다.
5. 한 사용자가 다른 경매에 연속 입찰할 때 walletVersion이 단조 증가하는지 검증한다.

## 완료 기준

- Redis 성공 입찰과 Stream 이벤트는 분리될 수 없다.
- 경매별 sequence와 사용자별 walletVersion은 단조 증가한다.
- 기존 DB 경로의 동작과 활성 프로필 선택은 회귀하지 않는다.
