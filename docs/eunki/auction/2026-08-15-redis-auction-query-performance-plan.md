# Redis Auction Query Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 경매 조회의 순차 Redis N+1 왕복과 정상 미참여 bidder key miss를 제거해 고부하에서 connection 및 Tomcat thread 포화를 완화한다.

**Architecture:** 기존 per-auction Redis key와 write Lua는 유지한다. 후보 state와 실제 참여자의 bidder state만 pipeline으로 batch 조회하고, 서비스는 이미 읽은 state를 재사용하며 목록에서 recent-bids를 읽지 않는다.

**Tech Stack:** Java 21, Spring Boot, Spring Data Redis `StringRedisTemplate`, JUnit 5, AssertJ, Mockito, Testcontainers Redis

## Global Constraints

- 작업 기준은 `origin/dev`의 `ab126014`이다.
- 카드 snapshot 전체 선적재는 #503 범위에 포함하지 않는다.
- Redis timeout/connection 오류를 DB fallback으로 바꾸지 않는다.
- Redis/DB 프로필의 기존 JSON 응답 계약, 정렬, 커서, 필터 결과를 유지한다.
- production code보다 실패 테스트를 먼저 작성하고 직접 RED를 확인한다.

---

### Task 1: State batch pipeline reader

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/query/RedisAuctionRealtimeStateReader.java`
- Modify: `backend/src/test/java/com/dbidding/auction/query/RedisAuctionRealtimeStateReaderTest.java`
- Create: `backend/src/test/java/com/dbidding/auction/query/RedisAuctionRealtimeStateReaderPipelineIntegrationTest.java`

**Interfaces:**
- Produces: `Map<Integer, AuctionState> readAuctionStates(List<Integer> auctionIds)`
- Produces: 단건과 batch가 공유하는 private state parser

- [ ] **Step 1: batch가 입력 id 순서의 유효 state만 반환하는 실패 통합 테스트 작성**

  실제 Redis에 유효 hash 2개, 없는 id 1개, 필수 field가 없는 hash 1개를 넣고 `readAuctionStates(List.of(4, 1, 3, 2))` 결과 key가 `1, 2` 순서이며 값이 올바른지 검증한다. 이 테스트는 메서드가 없어 compile failure가 나야 한다.

- [ ] **Step 2: RED 확인**

  Run: `./gradlew test --tests 'com.dbidding.auction.query.RedisAuctionRealtimeStateReaderPipelineIntegrationTest'`

  Expected: `readAuctionStates` symbol을 찾지 못해 실패.

- [ ] **Step 3: pipeline과 공용 parser 최소 구현**

  `executePipelined`에서 각 `auction:state:{id}`의 `HGETALL`을 호출하고 입력 id와 결과 index를 결합한다. `LinkedHashMap`에 유효 state만 넣고, 단건 `readAuctionState`도 같은 parser를 사용한다.

- [ ] **Step 4: reader 테스트 통과 확인**

  Run: `./gradlew test --tests 'com.dbidding.auction.query.RedisAuctionRealtimeStateReaderTest' --tests 'com.dbidding.auction.query.RedisAuctionRealtimeStateReaderPipelineIntegrationTest'`

  Expected: PASS.

- [ ] **Step 5: 마이크로 커밋**

  Commit subject: `perf: Redis 경매 상태를 pipeline으로 일괄 조회`

### Task 2: List-only bidder state batch lookup

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/query/RedisAuctionRealtimeStateReader.java`
- Modify: `backend/src/test/java/com/dbidding/auction/query/RedisAuctionRealtimeStateReaderPipelineIntegrationTest.java`

**Interfaces:**
- Produces: `Map<Integer, MyBidState> readMyBidStates(List<Integer> auctionIds, Integer userId)`
- Produces: `record MyBidState(MyBidStatus status, Long amount)`

- [ ] **Step 1: 미참여 hash를 조회하지 않고 참여 경매만 반환하는 실패 테스트 작성**

  참여 SET에는 id 2만 넣고 bidder hash는 id 2에만 만든다. `readMyBidStates(List.of(1, 2, 3), userId)`가 id 2만 반환하고 상태/금액이 일치하는지 검증한다. 익명 userId는 빈 map을 반환하는 별도 테스트를 둔다.

- [ ] **Step 2: RED 확인**

  Run: `./gradlew test --tests 'com.dbidding.auction.query.RedisAuctionRealtimeStateReaderPipelineIntegrationTest'`

  Expected: batch bidder API가 없어 실패.

- [ ] **Step 3: SMISMEMBER와 선택적 bidder pipeline 구현**

  `opsForSet().isMember(participatingKey, auctionIds.toArray())` 결과에서 true인 id만 추리고, 해당 bidder hash만 `executePipelined`로 읽는다. 파싱 실패는 map에서 제외하고 Redis command 예외는 전파한다.

- [ ] **Step 4: integration test 통과 확인**

  Run: `./gradlew test --tests 'com.dbidding.auction.query.RedisAuctionRealtimeStateReaderPipelineIntegrationTest'`

  Expected: PASS.

- [ ] **Step 5: 마이크로 커밋**

  Commit subject: `perf: 참여 경매의 사용자 입찰 상태만 조회`

### Task 3: Auction list consumes batch snapshots

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/service/AuctionQueryService.java`
- Modify: `backend/src/test/java/com/dbidding/auction/service/AuctionQueryServiceTest.java`

**Interfaces:**
- Consumes: `readAuctionStates(List<Integer>)`
- Consumes: `readMyBidStates(List<Integer>, Integer)`
- Changes: `redisSummary(AuctionState, MyBidState)`는 Redis를 추가 호출하지 않는다.

- [ ] **Step 1: 목록 응답이 batch state와 batch bidder 결과만 사용하는 실패 테스트 작성**

  reader가 후보 3개의 batch state와 사용자 상태 1개를 반환하도록 하고, 응답의 정렬과 각 `myBidStatus`/`myBidAmount`를 검증한다. 단건 `readAuctionState`와 `read`가 호출되면 테스트가 실패하도록 mock 기본 동작을 둔다.

- [ ] **Step 2: RED 확인**

  Run: `./gradlew test --tests 'com.dbidding.auction.service.AuctionQueryServiceTest'`

  Expected: service가 아직 batch API를 호출하지 않아 기대한 content 또는 strict mock 조건으로 실패.

- [ ] **Step 3: 목록 batch 소비 최소 구현**

  ZSET tuple id 목록을 `readAuctionStates`에 전달하고 tuple 순서로 state를 복원해 기존 필터/정렬/커서 코드를 적용한다. content id로 `readMyBidStates`를 한 번 호출하고 응답 조립에는 state와 `MyBidState`만 사용한다.

- [ ] **Step 4: 목록 정렬·커서·필터 회귀 테스트 통과 확인**

  Run: `./gradlew test --tests 'com.dbidding.auction.service.AuctionQueryServiceTest'`

  Expected: PASS.

- [ ] **Step 5: 마이크로 커밋**

  Commit subject: `perf: 경매 목록 Redis 중복 조회를 제거`

### Task 4: Single-read bid history and bid context

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/query/RedisAuctionRealtimeStateReader.java`
- Modify: `backend/src/main/java/com/dbidding/auction/service/AuctionQueryService.java`
- Modify: `backend/src/test/java/com/dbidding/auction/query/RedisAuctionRealtimeStateReaderTest.java`
- Modify: `backend/src/test/java/com/dbidding/auction/service/AuctionQueryServiceTest.java`

**Interfaces:**
- Produces: `StoredAuctionState readStoredAuctionState(Integer auctionId)` 또는 동등한 public aggregate API
- Produces: 저장 state를 받는 `readRealtimeState(StoredAuctionState, Integer userId, boolean includeRecentBids)`

- [ ] **Step 1: 단건 `read()`가 state hash를 한 번만 읽는 실패 테스트 작성**

  reader unit test에서 state hash 응답 한 번으로 highest bidder와 recent-bids 최고가 여부까지 계산되는지 검증한다. hash mock이 같은 state key에 두 번째 호출되면 실패하게 한다.

- [ ] **Step 2: 입찰 내역과 컨텍스트 hit 경로의 state 재사용 실패 테스트 작성**

  service test에서 저장 state를 한 번 반환하고 입찰 내역/컨텍스트 응답이 구성되는지 검증한다. hit 경로에서 seeder와 DB query service가 호출되면 실패하도록 구성한다.

- [ ] **Step 3: RED 확인**

  Run: `./gradlew test --tests 'com.dbidding.auction.query.RedisAuctionRealtimeStateReaderTest' --tests 'com.dbidding.auction.service.AuctionQueryServiceTest'`

  Expected: 현재 `readSnapshot` 중복 또는 새 aggregate API 부재로 실패.

- [ ] **Step 4: 저장 state aggregate와 service 재사용 구현**

  state fields를 한 번 파싱해 `AuctionState`와 `highestBidderId`를 함께 보관한다. getBids는 aggregate와 recent-bids만, getBidContext는 aggregate와 bidder/recent-bids만 읽는다. aggregate가 null일 때만 seed 후 한 번 다시 읽고, 그래도 null이면 기존 DB fallback을 사용한다.

- [ ] **Step 5: targeted tests 통과 확인**

  Run: `./gradlew test --tests 'com.dbidding.auction.query.RedisAuctionRealtimeStateReaderTest' --tests 'com.dbidding.auction.service.AuctionQueryServiceTest'`

  Expected: PASS.

- [ ] **Step 6: 마이크로 커밋**

  Commit subject: `perf: 입찰 조회의 Redis 상태 중복 읽기를 제거`

### Task 5: Error semantics and regression verification

**Files:**
- Modify: `backend/src/test/java/com/dbidding/auction/service/AuctionQueryServiceTest.java`
- Modify if needed: `backend/src/test/java/com/dbidding/auction/service/AuctionQueryConnectionPoolStarvationIntegrationTest.java`

**Interfaces:**
- Verifies: real miss만 seed/DB fallback을 사용하고 Redis command exception은 그대로 전파된다.

- [ ] **Step 1: Redis command 오류가 DB fallback을 호출하지 않는 실패 회귀 테스트 작성**

  batch state 또는 단건 aggregate 조회가 `RedisConnectionFailureException`을 던지게 하고 동일 예외가 전파되며 `dbAuctionQueryService`와 `stateSeeder`가 호출되지 않는지 검증한다.

- [ ] **Step 2: RED 또는 characterization 확인**

  Run: `./gradlew test --tests 'com.dbidding.auction.service.AuctionQueryServiceTest'`

  Expected: 새 batch 경로에서 예외를 잘못 삼키면 FAIL, 이미 올바르면 PASS인 characterization test로 유지.

- [ ] **Step 3: 필요한 최소 오류 처리 수정**

  cache miss는 null/empty 결과로만 분기하고 Spring Data Redis 예외를 catch하지 않는다.

- [ ] **Step 4: backend 전체 검증**

  Run: `./gradlew test`

  Expected: 모든 테스트 PASS, 실패 0.

- [ ] **Step 5: 로컬 부하 검증**

  기존 1000 사용자, 50/100/150/200/300/400 QPS, 목록/입찰 내역/입찰 4:4:2 시나리오를 동일 환경에서 실행한다. Redis connection active, Tomcat busy threads, Hikari active/pending, keyspace hit ratio, API p95/p99, 오류율을 변경 전 기록과 비교한다.

- [ ] **Step 6: 최종 마이크로 커밋**

  Commit subject: `test: Redis 경매 조회 성능 회귀를 검증`
