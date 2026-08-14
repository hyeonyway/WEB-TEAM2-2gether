# Redis 조회 트랜잭션 경계 분리 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redis 조회 및 cold-seed 대기 중 요청 스레드가 JDBC 커넥션을 보유하지 않게 하여 #501의 HikariCP 커넥션 기아를 제거한다.

**Architecture:** `AuctionQueryService`는 트랜잭션 없는 Redis/DB 조회 조정 서비스로 축소하고, DB fallback과 entity 기반 DTO 조립은 `DbAuctionQueryService`의 짧은 read-only 트랜잭션으로 옮긴다. 경매 cold-seed의 DB 조회도 `AuctionSeedDataLoader`를 통해 batch thread에서만 트랜잭션을 시작하며, Redis 확인·future 대기·Lua 쓰기는 트랜잭션 밖에 둔다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, Spring Data Redis, HikariCP, MySQL 8.4 Testcontainers, Redis 7.4 Testcontainers, JUnit 5, AssertJ, Mockito

## Global Constraints

- 기준 브랜치는 `origin/dev`의 `f2c80c86`이며 작업 브랜치는 `fix/501-redis-cold-seed-connection-pool-starvation`이다.
- `CompletableFuture.join()`을 호출하는 요청 스레드에는 활성 DB 트랜잭션이 없어야 한다.
- Redis hit 확인, single-flight 대기, batch future 대기, Redis Lua 쓰기 중 JDBC 커넥션을 보유하지 않는다.
- DB fallback과 cold-seed DB 조회만 `readOnly = true` 트랜잭션에서 수행한다.
- controller API, 응답 DTO, Redis key, timeout/fallback 정책을 변경하지 않는다.
- Redis N+1, pipeline/Lua 합치기, TTL, warm-up 500개, pool/thread 크기 조정은 범위에서 제외한다.
- 테스트는 Hikari maximum pool size 2에서 재현되는 실패를 먼저 확인한 뒤 구현한다.
- task별 관련 파일만 stage하고 아래 형식으로 마이크로 커밋한다.
- 문서는 `docs/eunki` 아래에만 둔다.

---

## 파일 구조

- `backend/src/main/java/com/dbidding/auction/service/AuctionQueryService.java`
  - 트랜잭션 없는 public 조회 조정과 Redis 조회/Redis DTO 변환만 담당한다.
- `backend/src/main/java/com/dbidding/auction/service/DbAuctionQueryService.java`
  - repository 조회, card 조회, entity 기반 DTO 변환, DB fallback을 하나의 read-only 경계에서 담당한다.
- `backend/src/main/java/com/dbidding/auction/bid/AuctionSeedDbData.java`
  - 트랜잭션 안에서 읽은 경매, 최고 입찰, 최신/최근 입찰, 이미지 경로를 경매별로 묶는다.
- `backend/src/main/java/com/dbidding/auction/bid/AuctionSeedDataLoader.java`
  - batch thread에서 경매 seed용 MySQL 조회를 한 번의 read-only 트랜잭션으로 수행한다.
- `backend/src/main/java/com/dbidding/auction/bid/AuctionSeedData.java`
  - DB loader 결과에 트랜잭션 밖에서 읽은 card snapshot을 결합한 최종 seed 입력이다.
- `backend/src/main/java/com/dbidding/auction/bid/RedisAuctionSeedBatchCoordinator.java`
  - ID를 모으고 loader와 card reader를 순서대로 호출해 future를 완료한다.
- `backend/src/main/java/com/dbidding/auction/bid/RedisAuctionStateSeeder.java`
  - Redis miss/single-flight/join/Lua seed를 트랜잭션 없이 수행한다.
- `backend/src/test/java/com/dbidding/auction/service/AuctionQueryConnectionPoolStarvationIntegrationTest.java`
  - pool size 2에서 request thread가 커넥션을 선점하지 않는지 검증한다.
- `backend/src/test/java/com/dbidding/auction/service/DbAuctionQueryServiceTest.java`
  - 기존 DB 조회 응답 계약을 분리된 서비스에서 검증한다.
- `backend/src/test/java/com/dbidding/auction/service/AuctionQueryServiceTest.java`
  - Redis/DB 조회 조정, cursor, Redis DTO 계약만 검증한다.
- `backend/src/test/java/com/dbidding/auction/service/DbAuctionQueryTransactionIntegrationTest.java`
  - DB collaborator의 실제 read-only 트랜잭션과 DTO 완결성을 검증한다.
- `backend/src/test/java/com/dbidding/auction/bid/AuctionSeedDataLoaderTest.java`
  - DB batch 결과 묶기와 트랜잭션 경계를 검증한다.
- `backend/src/test/java/com/dbidding/auction/bid/RedisAuctionSeedBatchCoordinatorTest.java`
  - batch coordinator가 loader 1회와 card cache 조회 1회로 future를 완료하는지 검증한다.
- `backend/src/test/java/com/dbidding/auction/bid/RedisAuctionColdSeedConnectionPoolIntegrationTest.java`
  - 실제 MySQL/Redis cold miss 동시 요청이 pool size 2에서 완료되는지 검증한다.
- `backend/src/test/java/com/dbidding/auction/bid/RedisAuctionStateSeederTest.java`
  - startup warm-up과 Lua seed 기존 계약을 새 loader 구조에서 유지한다.

---

### Task 1: 요청 스레드 커넥션 기아를 실패 테스트로 고정

**Files:**
- Create: `backend/src/test/java/com/dbidding/auction/service/AuctionQueryConnectionPoolStarvationIntegrationTest.java`

**Interfaces:**
- Consumes: 현재 `AuctionQueryService#getBidContext(Integer, Integer)`와 클래스 레벨 read-only 트랜잭션
- Produces: Redis 경로 진입 시 트랜잭션과 JDBC 커넥션을 보유하지 않아야 한다는 executable regression contract

- [ ] **Step 1: MySQL pool size 2 테스트 context를 작성한다**

  `@SpringBootTest`에 아래 속성과 MySQL 8.4 `@ServiceConnection`을 선언한다. scheduler가 background DB 작업을 만들지 않게 비활성화한다.

  ```java
  @Testcontainers(disabledWithoutDocker = true)
  @SpringBootTest(properties = {
          "statistic.scheduler.enabled=false",
          "auction.closing.scheduler.enabled=false",
          "auction.deadline.scheduler.enabled=false",
          "spring.datasource.hikari.maximum-pool-size=2",
          "spring.datasource.hikari.minimum-idle=0",
          "spring.datasource.hikari.connection-timeout=500",
          "spring.sql.init.mode=always",
          "spring.jpa.hibernate.ddl-auto=validate"
  })
  class AuctionQueryConnectionPoolStarvationIntegrationTest {
      @Container
      @ServiceConnection
      static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
              .withDatabaseName("dbidding");
  }
  ```

- [ ] **Step 2: Redis hit 경로가 트랜잭션 없이 wallet collaborator를 호출하는 실패 테스트를 작성한다**

  `RedisAuctionRealtimeStateReader`와 `WalletService`를 `@MockitoBean`으로 교체한다. reader는 완전한 `AuctionState`와 `RealtimeState`를 반환한다. wallet answer 안에서 현재 트랜잭션과 Hikari active를 기록한다.

  ```java
  AtomicBoolean transactionActive = new AtomicBoolean();
  AtomicInteger activeConnections = new AtomicInteger();
  given(walletService.getBalance(7)).willAnswer(invocation -> {
      transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
      activeConnections.set(dataSource.getHikariPoolMXBean().getActiveConnections());
      return new WalletBalanceResponse(100_000L, 20_000L, 80_000L);
  });

  BidResponses.BidContext result = auctionQueryService.getBidContext(7, 101);

  assertThat(result.auctionId()).isEqualTo(101);
  assertThat(transactionActive).isFalse();
  assertThat(activeConnections).hasValue(0);
  ```

- [ ] **Step 3: 테스트가 현재 코드에서 실패하는지 확인한다**

  Run:

  ```bash
  cd backend
  ./gradlew test --tests '*AuctionQueryConnectionPoolStarvationIntegrationTest.Redis_hit*'
  ```

  Expected: `transactionActive` 또는 `activeConnections` assertion이 실패하며, 클래스 레벨 `@Transactional(readOnly = true)`가 Redis hit에도 적용됨을 보여야 한다.

- [ ] **Step 4: 실제 기아 형태의 동시성 실패 테스트를 추가한다**

  두 request를 latch로 동시에 시작하고, wallet answer는 별도 `batchExecutor`에서 `JdbcTemplate.queryForObject("SELECT 1", Integer.class)`를 실행한 future를 기다린다.

  ```java
  given(walletService.getBalance(anyInt())).willAnswer(invocation -> {
      Future<Integer> dbBatch = batchExecutor.submit(
              () -> jdbcTemplate.queryForObject("SELECT 1", Integer.class));
      return new WalletBalanceResponse(dbBatch.get(2, TimeUnit.SECONDS), 0L, 1L);
  });

  List<Future<BidResponses.BidContext>> requests = List.of(
          requestExecutor.submit(() -> awaitAndGet(start, 7, 101)),
          requestExecutor.submit(() -> awaitAndGet(start, 8, 102)));
  start.countDown();

  assertThat(requests).allSatisfy(request -> assertThat(request.get(3, TimeUnit.SECONDS)).isNotNull());
  ```

- [ ] **Step 5: 동시성 테스트가 현재 코드에서 pool timeout으로 실패하는지 확인한다**

  Run:

  ```bash
  cd backend
  ./gradlew test --tests '*AuctionQueryConnectionPoolStarvationIntegrationTest.제한된*'
  ```

  Expected: 두 request transaction이 pool의 두 connection을 잡아 batch future가 `CannotGetJdbcConnectionException` 또는 timeout으로 실패한다.

- [ ] **Step 6: 실패 테스트만 커밋한다**

  ```bash
  git add backend/src/test/java/com/dbidding/auction/service/AuctionQueryConnectionPoolStarvationIntegrationTest.java
  git commit -m "test: Redis 조회 커넥션 기아 회귀 테스트 추가" -m "- pool size 2에서 요청 트랜잭션의 커넥션 선점을 재현한다
  - Redis hit 경로의 트랜잭션 및 Hikari active 상태를 검증한다

  관련 이슈: #501"
  ```

---

### Task 2: DB 조회와 응답 조립을 독립 read-only 서비스로 추출

**Files:**
- Create: `backend/src/main/java/com/dbidding/auction/service/DbAuctionQueryService.java`
- Create: `backend/src/test/java/com/dbidding/auction/service/DbAuctionQueryServiceTest.java`
- Modify: `backend/src/test/java/com/dbidding/auction/service/AuctionRegistrationDetailContractTest.java`

**Interfaces:**
- Consumes: `AuctionRepository`, `AuctionImageRepository`, `BidRepository`, `CardService`, `AuctionCursorCodec`, `Clock`
- Produces:
  - `CursorPage<AuctionSummary> search(Integer userId, AuctionSearchRequest request)`
  - `List<DashboardAuction> getDashboardAuctions(Integer userId)`
  - `List<FailedAuctionSummary> getFailedAuctions(Integer sellerId)`
  - `AuctionDetail getDetail(Integer userId, Integer auctionId)`
  - `Page<BidSummary> getBids(Integer auctionId, PageRequestDto request)`
  - `BidContext getBidContext(Integer userId, Integer auctionId, WalletBalanceResponse wallet)`

- [ ] **Step 1: 기존 DB 응답 계약 테스트를 새 test class로 이동한다**

  `AuctionQueryServiceTest`에서 repository/card mock을 사용하는 다음 테스트를 `DbAuctionQueryServiceTest`로 옮기고 setup에서 새 서비스를 생성한다.

  ```java
  dbAuctionQueryService = new DbAuctionQueryService(
          auctionRepository,
          auctionImageRepository,
          bidRepository,
          cardService,
          cursorCodec,
          clock
  );
  ```

  이동 대상은 DB search cursor 4건, dashboard/failed 조회, detail 6건, ended bid 최고가 표시,
  DB bid-context의 `WON -> LEADING` 변환이다. bid-context 테스트는 wallet repository 호출 대신
  아래 값을 직접 전달한다.

  ```java
  WalletBalanceResponse wallet = new WalletBalanceResponse(145_000L, 45_000L, 100_000L);
  BidResponses.BidContext response = dbAuctionQueryService.getBidContext(3, 1, wallet);
  assertThat(response.myBidStatus()).isEqualTo(MyBidStatus.LEADING);
  ```

- [ ] **Step 2: 새 테스트가 compile failure인지 확인한다**

  Run:

  ```bash
  cd backend
  ./gradlew test --tests '*DbAuctionQueryServiceTest'
  ```

  Expected: `DbAuctionQueryService` type을 찾을 수 없어 compilation이 실패한다.

- [ ] **Step 3: `DbAuctionQueryService`를 만들고 DB 로직을 이동한다**

  클래스와 public boundary를 아래처럼 선언한다.

  ```java
  @Service
  @Transactional(readOnly = true)
  @RequiredArgsConstructor
  public class DbAuctionQueryService {
      private final AuctionRepository auctionRepository;
      private final AuctionImageRepository auctionImageRepository;
      private final BidRepository bidRepository;
      private final CardService cardService;
      private final AuctionCursorCodec auctionCursorCodec;
      private final Clock clock;

      public BidResponses.BidContext getBidContext(
              Integer userId, Integer auctionId, WalletBalanceResponse wallet) {
          Auction auction = getAuction(auctionId);
          Bid myBid = currentUserBid(userId, auctionId).orElse(null);
          List<BidResponses.BidSummary> recentBids = getBids(
                  auctionId, new PageRequestDto(0, 5)).content();
          return BidResponses.BidContext.builder()
                  .auctionId(auctionId)
                  .status(auction.getStatus())
                  .currentPrice(auction.getCurrentPrice())
                  .minimumBid(auction.minimumBid())
                  .bidIncrement(auction.getBidPriceUnit())
                  .myBidStatus(myBidStatus(myBid))
                  .myBidAmount(myBid == null ? null : myBid.getBidPrice())
                  .wallet(new BidResponses.WalletSummary(
                          wallet.availableBalance(), wallet.frozenBalance()))
                  .recentBids(recentBids)
                  .build();
      }
  }
  ```

  search/detail/bids/dashboard/failed와 entity helper/mapper는 현재 구현을 동작 변경 없이
  이동한다. Redis reader와 Redis state type은 이 클래스에 import하지 않는다.

- [ ] **Step 4: DB 서비스 계약 테스트를 통과시킨다**

  Run:

  ```bash
  cd backend
  ./gradlew test --tests '*DbAuctionQueryServiceTest'
  ```

  Expected: 모든 DB 조회와 DTO 계약 테스트 PASS.

- [ ] **Step 5: 등록 상세 계약 테스트를 새 구성으로 변경한다**

  test fixture에서 `DbAuctionQueryService`를 생성하고 조회 조정 서비스 대신 DB collaborator의
  `getDetail(null, 1)`을 호출한다. seller memo, PSA certification, nullable buy-now assertion은
  그대로 유지한다.

- [ ] **Step 6: DB 추출을 커밋한다**

  ```bash
  git add backend/src/main/java/com/dbidding/auction/service/DbAuctionQueryService.java backend/src/test/java/com/dbidding/auction/service/DbAuctionQueryServiceTest.java backend/src/test/java/com/dbidding/auction/service/AuctionRegistrationDetailContractTest.java
  git commit -m "refactor: 경매 DB 조회 서비스를 분리" -m "- DB fallback과 entity 기반 DTO 조립을 read-only 서비스로 이동한다
  - 기존 조회 및 등록 상세 응답 계약을 분리된 서비스에서 검증한다

  관련 이슈: #501"
  ```

---

### Task 3: `AuctionQueryService`를 트랜잭션 없는 Redis/DB 조회 조정 서비스로 축소

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/service/AuctionQueryService.java`
- Modify: `backend/src/test/java/com/dbidding/auction/service/AuctionQueryServiceTest.java`
- Modify: `backend/src/test/java/com/dbidding/auction/service/AuctionQueryConnectionPoolStarvationIntegrationTest.java`

**Interfaces:**
- Consumes: Task 2의 `DbAuctionQueryService` public methods, `WalletService`, optional Redis reader/seeder
- Produces: 기존 controller가 호출하는 `AuctionQueryService` public API를 동일 signature로 유지

- [ ] **Step 1: 조회 경로 분기 실패 테스트를 작성한다**

  Redis reader가 없을 때 각 public method가 정확히 DB collaborator로 위임되는지 검증한다.

  ```java
  given(dbAuctionQueryService.getBids(101, request)).willReturn(expected);

  AuctionResponses.Page<BidResponses.BidSummary> actual =
          auctionQueryService.getBids(101, request);

  assertThat(actual).isSameAs(expected);
  then(dbAuctionQueryService).should().getBids(101, request);
  then(auctionRepository).shouldHaveNoInteractions();
  ```

  `search`, `getDashboardAuctions`, `getFailedAuctions`, `getDetail`, `getBids`에도 같은 위임
  assertion을 둔다. `getBidContext`는 wallet을 조회 조정 서비스에서 먼저 조회한 후 아래 signature로
  전달하는지 확인한다.

  ```java
  then(dbAuctionQueryService).should().getBidContext(7, 101, walletBalance);
  ```

- [ ] **Step 2: 조회 조정 테스트가 현재 constructor와 직접 repository 사용 때문에 실패하는지 확인한다**

  Run:

  ```bash
  cd backend
  ./gradlew test --tests '*AuctionQueryServiceTest'
  ```

  Expected: 새 `DbAuctionQueryService` mock을 받지 않거나 DB repository를 직접 호출해 delegation verification이 실패한다.

- [ ] **Step 3: 클래스 트랜잭션과 repository 의존성을 제거한다**

  조회 조정 서비스의 field를 아래 범위로 줄인다.

  ```java
  @Service
  @RequiredArgsConstructor
  public class AuctionQueryService {
      private final WalletService walletService;
      private final DbAuctionQueryService dbAuctionQueryService;
      private final AuctionCursorCodec auctionCursorCodec;
      private final Clock clock;
      @Autowired(required = false)
      private RedisAuctionRealtimeStateReader realtimeStateReader;
      @Autowired(required = false)
      private RedisAuctionStateSeeder stateSeeder;
  }
  ```

  class와 public Redis method 어디에도 `@Transactional`을 두지 않는다.

- [ ] **Step 4: public routing을 DB collaborator 위임으로 변경한다**

  ```java
  public AuctionResponses.CursorPage<AuctionResponses.AuctionSummary> search(
          Integer userId, AuctionSearchRequest request) {
      return realtimeStateReader == null
              ? dbAuctionQueryService.search(userId, request)
              : searchRedisActiveAuctions(userId, request);
  }

  public AuctionResponses.Page<BidResponses.BidSummary> getBids(
          Integer auctionId, PageRequestDto request) {
      if (realtimeStateReader != null && realtimeStateReader.readAuctionState(auctionId) != null) {
          RedisAuctionRealtimeStateReader.RealtimeState realtime = realtimeStateReader.read(auctionId, null);
          if (realtime == null) throw AuctionException.notFound();
          List<BidResponses.BidSummary> content = realtime.recentBids();
          return new AuctionResponses.Page<>(content, 0, request.sizeOrDefault(), content.size(), false);
      }
      return dbAuctionQueryService.getBids(auctionId, request);
  }

  public BidResponses.BidContext getBidContext(Integer userId, Integer auctionId) {
      seedAuctionIfRequired(auctionId);
      WalletBalanceResponse wallet = walletService.getBalance(userId);
      RedisAuctionRealtimeStateReader.RealtimeState realtime =
              realtimeStateReader == null ? null : realtimeStateReader.read(auctionId, userId);
      if (realtime != null) return redisBidContext(auctionId, wallet, realtime);
      return dbAuctionQueryService.getBidContext(userId, auctionId, wallet);
  }
  ```

  `getDetail`은 seed/read 후 Redis state가 있으면 `redisDetail`, 없으면
  `dbAuctionQueryService.getDetail`을 호출한다. dashboard/failed는 항상 DB collaborator로
  위임한다.

- [ ] **Step 5: 조회 조정 서비스와 pool starvation 테스트를 실행한다**

  Run:

  ```bash
  cd backend
  ./gradlew test --tests '*AuctionQueryServiceTest' --tests '*AuctionQueryConnectionPoolStarvationIntegrationTest'
  ```

  Expected: Redis 조회 조정 계약 PASS, transaction active false, Hikari active 0, pool size 2 동시 request PASS.

- [ ] **Step 6: 조회 조정 서비스 분리를 커밋한다**

  ```bash
  git add backend/src/main/java/com/dbidding/auction/service/AuctionQueryService.java backend/src/test/java/com/dbidding/auction/service/AuctionQueryServiceTest.java backend/src/test/java/com/dbidding/auction/service/AuctionQueryConnectionPoolStarvationIntegrationTest.java
  git commit -m "fix: Redis 조회 중 DB 커넥션 선점을 제거" -m "- 경매 조회 조정 서비스의 클래스 트랜잭션을 제거한다
  - DB fallback을 별도 read-only 조회 서비스에 위임한다

  관련 이슈: #501"
  ```

---

### Task 4: 경매 cold-seed DB 트랜잭션을 batch thread로 이동

**Files:**
- Create: `backend/src/main/java/com/dbidding/auction/bid/AuctionSeedDbData.java`
- Create: `backend/src/main/java/com/dbidding/auction/bid/AuctionSeedDataLoader.java`
- Create: `backend/src/test/java/com/dbidding/auction/bid/AuctionSeedDataLoaderTest.java`
- Modify: `backend/src/main/java/com/dbidding/auction/bid/AuctionSeedData.java`
- Modify: `backend/src/main/java/com/dbidding/auction/bid/RedisAuctionSeedBatchCoordinator.java`
- Modify: `backend/src/main/java/com/dbidding/auction/bid/RedisAuctionStateSeeder.java`
- Modify: `backend/src/test/java/com/dbidding/auction/bid/RedisAuctionSeedBatchCoordinatorTest.java`
- Modify: `backend/src/test/java/com/dbidding/auction/bid/RedisAuctionStateSeederTest.java`

**Interfaces:**
- Consumes: auction/bid/image repositories와 `RedisCardStateReader#getCardSnapshots(Collection<Integer>)`
- Produces:
  - `Map<Integer, AuctionSeedDbData> AuctionSeedDataLoader.load(List<Integer> auctionIds)`
  - `CompletableFuture<Optional<AuctionSeedData>> RedisAuctionSeedBatchCoordinator.requestSeedData(Integer auctionId)`
  - transaction 없는 `boolean RedisAuctionStateSeeder.seedIfAbsent(Integer auctionId)`

- [ ] **Step 1: loader와 coordinator의 실패 테스트를 작성한다**

  loader mock을 coordinator에 주입하고 20개 ID를 동시에 요청했을 때 loader와 card reader가
  각각 한 번만 호출되는지 검증한다.

  ```java
  verify(dataLoader, times(1)).load(argThat(ids -> ids.size() == 20));
  verify(cardStateReader, times(1)).getCardSnapshots(argThat(ids -> ids.size() == 20));
  verifyNoInteractions(auctionRepository, bidRepository, auctionImageRepository);
  ```

  loader test는 repository batch 결과를 입력하고 경매별 leading/latest/recent/images가 올바르게
  묶이는지 확인한다.

  ```java
  Map<Integer, AuctionSeedDbData> result = dataLoader.load(List.of(101, 102));

  assertThat(result.get(101).leading()).isSameAs(leading101);
  assertThat(result.get(101).imagePaths()).containsExactly("/101-front.png");
  assertThat(result.get(102).latestBids()).containsExactly(latest102);
  ```

- [ ] **Step 2: 새 loader type이 없어 compilation failure인지 확인한다**

  Run:

  ```bash
  cd backend
  ./gradlew test --tests '*AuctionSeedDataLoaderTest' --tests '*RedisAuctionSeedBatchCoordinatorTest'
  ```

  Expected: `AuctionSeedDataLoader`와 `AuctionSeedDbData` type을 찾을 수 없어 compilation이 실패한다.

- [ ] **Step 3: DB-only record와 loader를 구현한다**

  ```java
  public record AuctionSeedDbData(
          Auction auction,
          Bid leading,
          List<String> imagePaths,
          List<Bid> latestBids,
          List<Bid> recentBids
  ) {}
  ```

  ```java
  @Component
  @Profile("redis")
  @RequiredArgsConstructor
  public class AuctionSeedDataLoader {
      private final AuctionRepository auctionRepository;
      private final BidRepository bidRepository;
      private final AuctionImageRepository auctionImageRepository;

      @Transactional(readOnly = true)
      public Map<Integer, AuctionSeedDbData> load(List<Integer> auctionIds) {
          List<Auction> auctions = auctionRepository
                  .findByIdInAndStatusNot(auctionIds, AuctionStatus.ENDED).stream()
                  .filter(auction -> EnumSet.of(AuctionStatus.OPEN, AuctionStatus.ENDING)
                          .contains(auction.getStatus()))
                  .toList();
          if (auctions.isEmpty()) return Map.of();
          List<Integer> activeIds = auctions.stream().map(Auction::getId).toList();
          Map<Integer, Bid> leadingByAuction = bidRepository
                  .findByAuctionIdInAndStatus(activeIds, BidStatus.LEADING).stream()
                  .collect(Collectors.toMap(
                          bid -> bid.getAuction().getId(),
                          Function.identity(),
                          (first, ignored) -> first));
          Map<Integer, List<Bid>> latestByAuction = bidRepository
                  .findLatestBidPerBidderByAuctionIdIn(activeIds).stream()
                  .collect(Collectors.groupingBy(bid -> bid.getAuction().getId()));
          Map<Integer, List<Bid>> recentByAuction = bidRepository
                  .findRecentFiveByAuctionIdIn(activeIds).stream()
                  .collect(Collectors.groupingBy(bid -> bid.getAuction().getId()));
          Map<Integer, List<String>> imagesByAuction = auctionImageRepository
                  .findByAuctionIdInOrderById(activeIds).stream()
                  .collect(Collectors.groupingBy(
                          image -> image.getAuction().getId(),
                          Collectors.mapping(AuctionImage::getImagePath, Collectors.toList())));
          return auctions.stream().collect(Collectors.toMap(
                  Auction::getId,
                  auction -> new AuctionSeedDbData(
                          auction,
                          leadingByAuction.get(auction.getId()),
                          imagesByAuction.getOrDefault(auction.getId(), List.of()),
                          latestByAuction.getOrDefault(auction.getId(), List.of()),
                          recentByAuction.getOrDefault(auction.getId(), List.of()))));
      }
  }
  ```

  loader에서는 Redis/card I/O를 호출하지 않는다. 모든 entity 연관 접근과 grouping key 추출은
  method return 전에 완료한다.

- [ ] **Step 4: coordinator가 DB loader 뒤에 card snapshot을 결합하게 변경한다**

  ```java
  private void flush(Batch batch) {
      try {
          Map<Integer, AuctionSeedDbData> dbData = dataLoader.load(batch.auctionIds());
          Map<Integer, CardSnapshot> cards = cardStateReader.getCardSnapshots(
                  dbData.values().stream()
                          .map(data -> data.auction().getItemId())
                          .distinct()
                          .toList());
          batch.complete(auctionId -> Optional.ofNullable(dbData.get(auctionId))
                  .map(data -> new AuctionSeedData(
                          data.auction(), data.leading(),
                          cards.get(data.auction().getItemId()), data.imagePaths(),
                          data.latestBids(), data.recentBids())));
      } catch (Throwable throwable) {
          batch.completeExceptionally(throwable);
      }
  }
  ```

  coordinator constructor는 repository 3개 대신 `AuctionSeedDataLoader`와
  `RedisCardStateReader`를 받는다.

- [ ] **Step 5: state seeder에서 request transaction과 repository 의존성을 제거한다**

  `seedIfAbsent`와 `seedAllIfAbsent`의 `@Transactional`을 제거한다. 단건은 기존처럼
  coordinator future를 join한다. warm-up은 active ID들의 future를 먼저 모두 생성해 같은
  batch window에 들어가게 한 뒤 join한다.

  ```java
  List<CompletableFuture<Optional<AuctionSeedData>>> futures = active.stream()
          .map(Auction::getId)
          .map(batchCoordinator::requestSeedData)
          .toList();
  List<AuctionSeedData> seedData = futures.stream()
          .map(CompletableFuture::join)
          .flatMap(Optional::stream)
          .toList();
  seedData.forEach(data -> seed(data.auction(), data.leading(), data.card(),
          data.imagePaths(), data.latestBids(), data.recentBids()));
  return seedData.stream()
          .map(AuctionSeedData::leading)
          .filter(Objects::nonNull)
          .map(Bid::getBidderId)
          .distinct()
          .toList();
  ```

- [ ] **Step 6: loader/coordinator/seeder 단위 테스트를 실행한다**

  Run:

  ```bash
  cd backend
  ./gradlew test --tests '*AuctionSeedDataLoaderTest' --tests '*RedisAuctionSeedBatchCoordinatorTest' --tests '*RedisAuctionStateSeederTest'
  ```

  Expected: batch 1회, early flush, 종료 경매 empty, exception propagation, event sequence seed 계약 모두 PASS.

- [ ] **Step 7: cold-seed boundary를 커밋한다**

  ```bash
  git add backend/src/main/java/com/dbidding/auction/bid/AuctionSeedDbData.java backend/src/main/java/com/dbidding/auction/bid/AuctionSeedDataLoader.java backend/src/main/java/com/dbidding/auction/bid/AuctionSeedData.java backend/src/main/java/com/dbidding/auction/bid/RedisAuctionSeedBatchCoordinator.java backend/src/main/java/com/dbidding/auction/bid/RedisAuctionStateSeeder.java backend/src/test/java/com/dbidding/auction/bid/AuctionSeedDataLoaderTest.java backend/src/test/java/com/dbidding/auction/bid/RedisAuctionSeedBatchCoordinatorTest.java backend/src/test/java/com/dbidding/auction/bid/RedisAuctionStateSeederTest.java
  git commit -m "fix: 경매 cold seed 트랜잭션을 배치 스레드로 이동" -m "- 요청 스레드의 seed 트랜잭션과 future 대기 중 커넥션 보유를 제거한다
  - DB loader와 Redis card/seed 단계를 분리한다

  관련 이슈: #501"
  ```

---

### Task 5: 실제 MySQL/Redis cold miss와 DB fallback 트랜잭션을 통합 검증

**Files:**
- Create: `backend/src/test/java/com/dbidding/auction/bid/RedisAuctionColdSeedConnectionPoolIntegrationTest.java`
- Create: `backend/src/test/java/com/dbidding/auction/service/DbAuctionQueryTransactionIntegrationTest.java`

**Interfaces:**
- Consumes: Task 2~4의 Spring bean과 실제 Hikari/MySQL/Redis
- Produces: pool size 2 cold miss 완료, Redis key 생성, DB fallback read-only, DTO transaction 완결성 증거

- [ ] **Step 1: MySQL과 Redis container를 함께 쓰는 cold miss test context를 작성한다**

  ```java
  @Testcontainers(disabledWithoutDocker = true)
  @SpringBootTest(properties = {
          "spring.profiles.active=redis",
          "auction.state-seeding.warm-up.enabled=false",
          "auction.closing.scheduler.enabled=false",
          "auction.deadline.scheduler.enabled=false",
          "statistic.scheduler.enabled=false",
          "spring.datasource.hikari.maximum-pool-size=2",
          "spring.datasource.hikari.minimum-idle=0",
          "spring.datasource.hikari.connection-timeout=1000",
          "spring.sql.init.mode=always",
          "spring.jpa.hibernate.ddl-auto=validate"
  })
  class RedisAuctionColdSeedConnectionPoolIntegrationTest {
      @Container @ServiceConnection
      static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
              .withDatabaseName("dbidding");
      @Container
      static final GenericContainer<?> REDIS =
              new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

      @DynamicPropertySource
      static void redisProperties(DynamicPropertyRegistry registry) {
          registry.add("spring.data.redis.host", REDIS::getHost);
          registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
      }
  }
  ```

- [ ] **Step 2: 실제 fixture와 동시 cold miss 검증을 작성한다**

  서로 다른 active auction 8개, card metadata, wallet 8개를 JDBC로 넣는다. 관련
  `auction:state:*`, `card:cache:*`, `wallet:balance:*`만 삭제한 뒤 8개 ID의
  `stateSeeder.seedIfAbsent(id)`를 동시에 실행한다. catch-up verifier는 `@MockitoBean`으로
  `true`를 반환하게 한다.

  ```java
  List<Future<Boolean>> futures = auctionIds.stream()
          .map(id -> executor.submit(() -> {
              start.await();
              return stateSeeder.seedIfAbsent(id);
          }))
          .toList();
  start.countDown();

  assertThat(futures).allSatisfy(future -> assertThat(future.get(5, SECONDS)).isTrue());
  assertThat(auctionIds).allSatisfy(id ->
          assertThat(redisTemplate.hasKey("auction:state:" + id)).isTrue());
  assertThat(hikariDataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
  ```

  cleanup은 test 전용 ID의 Redis key와 MySQL row만 명시적으로 삭제한다. `flushAll`은 사용하지
  않는다.

- [ ] **Step 3: DB fallback의 read-only transaction test를 작성한다**

  `DbAuctionQueryService`를 실제 Spring proxy로 호출하고, `CardService`를 `@MockitoBean`으로
  교체해 answer 안에서 transaction 상태를 기록한다.

  ```java
  given(cardService.getCardSnapshot(CARD_ID)).willAnswer(invocation -> {
      transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
      readOnly.set(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
      return cardSnapshot;
  });

  AuctionResponses.AuctionDetail detail = dbAuctionQueryService.getDetail(null, AUCTION_ID);
  String serialized = objectMapper.writeValueAsString(detail);

  assertThat(transactionActive).isTrue();
  assertThat(readOnly).isTrue();
  assertThat(serialized).contains("auctionId");
  ```

- [ ] **Step 4: 두 통합 테스트를 실행한다**

  Run:

  ```bash
  cd backend
  ./gradlew test --tests '*RedisAuctionColdSeedConnectionPoolIntegrationTest' --tests '*DbAuctionQueryTransactionIntegrationTest'
  ```

  Expected: pool timeout 0, pending 0, 8개 Redis state 생성, DB fallback transaction active/read-only, transaction 밖 serialization PASS.

- [ ] **Step 5: 통합 검증을 커밋한다**

  ```bash
  git add backend/src/test/java/com/dbidding/auction/bid/RedisAuctionColdSeedConnectionPoolIntegrationTest.java backend/src/test/java/com/dbidding/auction/service/DbAuctionQueryTransactionIntegrationTest.java
  git commit -m "test: Redis cold miss 트랜잭션 경계를 통합 검증" -m "- 실제 MySQL과 Redis에서 pool size 2 동시 cold miss를 검증한다
  - DB fallback의 read-only 트랜잭션과 DTO 완결성을 확인한다

  관련 이슈: #501"
  ```

---

### Task 6: 전체 회귀와 실제 혼합 부하 검증

**Files:**
- Read only: `backend/src/test/k6/bid/auction-bid-http.js`
- Do not stage: `backend/src/test/k6/bid/auction-bid-http.js`, existing untracked docs, unrelated user files

**Interfaces:**
- Consumes: Task 1~5의 완료된 implementation과 기존 load scenario
- Produces: unit/integration/full-suite 결과와 50~400 QPS before/after 운영 지표 비교

- [ ] **Step 1: 관련 unit와 Lua integration suite를 실행한다**

  Run:

  ```bash
  cd backend
  ./gradlew test --tests '*AuctionQueryServiceTest' --tests '*DbAuctionQueryServiceTest' --tests '*AuctionQueryConnectionPoolStarvationIntegrationTest' --tests '*DbAuctionQueryTransactionIntegrationTest' --tests '*AuctionSeedDataLoaderTest' --tests '*RedisAuctionSeedBatchCoordinatorTest' --tests '*RedisAuctionStateSeederTest' --tests '*RedisWalletStateSeederTest' --tests '*RedisWalletSeedBatchCoordinatorTest' --tests '*RedisAuctionStateSeedLuaIntegrationTest' --tests '*WalletBootstrapLuaIntegrationTest' --tests '*RedisAuctionColdSeedConnectionPoolIntegrationTest'
  ```

  Expected: selected tests all PASS; Docker가 없으면 Testcontainers tests는 disabled 사유를 명시하고 Docker 환경에서 다시 실행한다.

- [ ] **Step 2: backend 전체 테스트를 실행한다**

  Run:

  ```bash
  cd backend
  ./gradlew test
  ```

  Expected: `BUILD SUCCESSFUL`, unrelated test failure 0.

- [ ] **Step 3: 동일한 혼합 부하를 warm/cold 각각 실행한다**

  사용자 1,000명, 목록:입찰 내역:입찰 참여 `4:4:2`, QPS
  `50 → 100 → 150 → 200 → 300 → 400`, 각 단계는 서버 환경 설정인 5초를 유지한다.
  cold run은 test 전용 auction/wallet/card key만 삭제하고, warm run은 같은 dataset으로 key를
  유지한다.

  ```bash
  cd backend
  k6 run -e BASE_URL=http://localhost:8080 -e USER_COUNT=1000 src/test/k6/bid/auction-bid-http.js
  ```

  기존 k6 파일이 사용자 미추적 파일이면 수정하거나 stage하지 않고 실행만 한다.

- [ ] **Step 4: 부하 중 지표와 DB 로그를 비교한다**

  각 QPS 단계별로 다음 값을 표로 기록한다.

  ```text
  qps | api p95/p99 | error% | hikari active/pending | tomcat busy | redis hit ratio | slow query count
   50 |             |        |                       |             |                 |
  100 |             |        |                       |             |                 |
  150 |             |        |                       |             |                 |
  200 |             |        |                       |             |                 |
  300 |             |        |                       |             |                 |
  400 |             |        |                       |             |                 |
  ```

  합격 기준:

  - Hikari `pending = 0`
  - Hikari connection timeout 로그 0
  - Tomcat max thread 고갈 0
  - 부하 종료 후 Hikari active가 정상 수준으로 즉시 복귀
  - cache miss가 아닌 timeout을 이유로 DB fallback하는 호출 0
  - 남은 Redis N+1 지연은 수치와 endpoint만 별도 이슈로 기록

- [ ] **Step 5: diff와 stage 범위를 최종 확인한다**

  Run:

  ```bash
  git diff --check
  git status --short
  git diff origin/dev...HEAD --stat
  ```

  Expected: #501 문서, 서비스, seed loader, 관련 테스트만 변경되고 사용자 미추적 파일은 stage되지 않는다.

- [ ] **Step 6: 실패가 있으면 해당 task의 red/green cycle로 돌아간다**

  관련 테스트 또는 부하 합격 기준이 실패하면 새 범위로 우회하지 않는다. 실패 stack과
  Hikari/Redis/MySQL 지표로 원인을 좁힌 뒤 Task 2~5 중 해당 경계의 테스트를 먼저 강화하고,
  그 task에 명시된 production/test 파일만 수정·검증·커밋한다. 모든 합격 기준을 만족하면
  빈 커밋 없이 최종 상태 검토로 진행한다.

### Task 7: DB 입찰 컨텍스트의 단일 스냅샷을 복구

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/service/AuctionQueryService.java:248-260`
- Modify: `backend/src/main/java/com/dbidding/auction/service/DbAuctionQueryService.java:42-48,141-158`
- Modify: `backend/src/test/java/com/dbidding/auction/service/DbAuctionQueryTransactionIntegrationTest.java`
- Modify: `backend/src/test/java/com/dbidding/auction/service/DbAuctionQueryServiceTest.java`
- Modify: `backend/src/test/java/com/dbidding/auction/service/AuctionQueryServiceTest.java`
- Modify: `backend/src/test/java/com/dbidding/auction/service/AuctionRegistrationDetailContractTest.java`

**Interfaces:**
- Consumes: `WalletService#getBalance(Integer)`와 기존 `getBidContext(Integer, Integer, WalletBalanceResponse)` Redis fallback 진입점
- Produces: `BidContext getBidContext(Integer userId, Integer auctionId)` DB 전용 동일 스냅샷 진입점

- [ ] **Step 1: 지갑과 경매 조회의 동일 트랜잭션 회귀 테스트를 작성한다**

  `DbAuctionQueryTransactionIntegrationTest`에서 `WalletService`와 `BidRepository`를
  `@MockitoBean`으로 교체한다. 지갑 answer는 `JdbcTemplate`로 `SELECT 1`을 실행해 실제
  connection resource를 획득하고, 입찰 repository answer와 동일 resource인지 비교한다.

  ```java
  AtomicReference<Object> walletConnectionResource = new AtomicReference<>();
  AtomicReference<Object> bidConnectionResource = new AtomicReference<>();
  given(walletService.getBalance(USER_ID)).willAnswer(invocation -> {
      jdbcTemplate.queryForObject("SELECT 1", Integer.class);
      walletConnectionResource.set(TransactionSynchronizationManager.getResource(dataSource));
      return new WalletBalanceResponse(100_000L, 20_000L, 80_000L);
  });
  given(bidRepository.findFirstByAuctionIdAndBidderIdOrderByCreatedAtDescIdDesc(
          AUCTION_ID, USER_ID)).willAnswer(invocation -> {
      bidConnectionResource.set(TransactionSynchronizationManager.getResource(dataSource));
      return Optional.empty();
  });

  BidResponses.BidContext response = service.getBidContext(USER_ID, AUCTION_ID);

  assertThat(response.wallet().availableBalance()).isEqualTo(80_000L);
  assertThat(walletConnectionResource.get()).isNotNull();
  assertThat(bidConnectionResource.get()).isSameAs(walletConnectionResource.get());
  ```

- [ ] **Step 2: 새 테스트가 현재 구현에서 실패하는지 확인한다**

  Run:

  ```bash
  cd backend
  ./gradlew test --tests '*DbAuctionQueryTransactionIntegrationTest.DB_입찰_컨텍스트*'
  ```

  Expected: wallet을 내부에서 조회하는 2-argument `getBidContext`가 없어 compilation failure.

- [ ] **Step 3: DB 전용 진입점을 최소 구현한다**

  `DbAuctionQueryService`에 `WalletService`를 주입하고 아래 overload를 추가한다. 클래스의
  `@Transactional(readOnly = true)` proxy가 시작된 뒤 wallet 조회가 실행되므로 기본
  `PROPAGATION_REQUIRED`가 동일 transaction에 참여한다.

  ```java
  public BidResponses.BidContext getBidContext(Integer userId, Integer auctionId) {
      return getBidContext(userId, auctionId, walletService.getBalance(userId));
  }
  ```

  `AuctionQueryService`는 Redis reader가 없을 때 이 overload로 즉시 위임하고, Redis reader가
  있을 때만 기존처럼 cold seed와 Redis wallet 조회를 트랜잭션 밖에서 수행한다.

  ```java
  public BidResponses.BidContext getBidContext(Integer userId, Integer auctionId) {
      if (realtimeStateReader == null) {
          return dbAuctionQueryService.getBidContext(userId, auctionId);
      }
      seedAuctionIfRequired(auctionId);
      WalletBalanceResponse wallet = walletService.getBalance(userId);
      // 기존 Redis hit와 Redis 복구 후 DB fallback 로직 유지
  }
  ```

- [ ] **Step 4: 생성자 기반 테스트 fixture에 WalletService를 전달한다**

  `new DbAuctionQueryService(...)`를 사용하는 세 테스트에서 기존 `walletService` mock을 마지막
  생성자 인수로 전달한다. DB 응답 계약 테스트의 3-argument fallback 검증은 그대로 유지해
  Redis fallback이 지갑을 두 번 읽지 않는 계약도 보존한다.

- [ ] **Step 5: RED 테스트와 조회 계약 테스트를 통과시킨다**

  Run:

  ```bash
  cd backend
  ./gradlew test --tests '*DbAuctionQueryTransactionIntegrationTest' --tests '*DbAuctionQueryServiceTest' --tests '*AuctionQueryServiceTest' --tests '*AuctionRegistrationDetailContractTest'
  ```

  Expected: 모든 테스트 PASS, 동일 connection resource assertion PASS.

- [ ] **Step 6: Redis 커넥션 기아 회귀와 전체 테스트를 실행한다**

  Run:

  ```bash
  cd backend
  ./gradlew test --rerun-tasks
  ```

  Expected: `BUILD SUCCESSFUL`; Redis hit/cold-seed 경로의 transaction/Hikari assertion과 전체
  API 계약 테스트가 모두 PASS.

- [ ] **Step 7: 정합성 수정과 테스트를 커밋한다**

  ```bash
  git add backend/src/main/java/com/dbidding/auction/service/AuctionQueryService.java backend/src/main/java/com/dbidding/auction/service/DbAuctionQueryService.java backend/src/test/java/com/dbidding/auction/service/AuctionQueryServiceTest.java backend/src/test/java/com/dbidding/auction/service/DbAuctionQueryServiceTest.java backend/src/test/java/com/dbidding/auction/service/AuctionRegistrationDetailContractTest.java backend/src/test/java/com/dbidding/auction/service/DbAuctionQueryTransactionIntegrationTest.java
  git commit -m "fix: DB 입찰 컨텍스트 조회 스냅샷을 통일" -m "- 지갑과 경매 조회를 하나의 read-only 트랜잭션에 참여시킨다
  - Redis cold seed와 fallback의 비트랜잭션 경계를 유지한다

  관련 이슈: #501"
  ```

---

## 완료 조건

- `AuctionQueryService`와 `RedisAuctionStateSeeder.seedIfAbsent()`가 transaction 없이 Redis I/O와 future 대기를 수행한다.
- DB fallback과 경매 cold-seed DB 조회는 각각 Spring proxy를 거친 read-only transaction 안에서만 실행된다.
- pool size 2 동시성 회귀 테스트가 변경 전 실패, 변경 후 통과한다.
- 실제 MySQL/Redis cold miss test에서 Hikari timeout/pending 없이 모든 state가 생성된다.
- 관련 테스트와 backend 전체 테스트가 통과한다.
- 50~400 QPS 혼합 부하에서 Hikari 30개 장기 점유와 Tomcat thread 고갈이 재현되지 않는다.
- Redis N+1 및 hit ratio 개선은 이번 commit에 섞이지 않는다.
- 비-Redis 프로필의 입찰 컨텍스트는 지갑과 경매·입찰을 동일한 read-only MySQL snapshot에서 조회한다.
