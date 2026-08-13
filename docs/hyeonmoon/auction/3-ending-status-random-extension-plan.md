# 마감임박(ENDING) 시간기준 전환 + 단발 랜덤연장 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** OPEN 경매의 `ENDING` 전환을 입찰 트리거에서 순수 시간 기준으로 바꾸고, 전환 시점에 1~2분 사이 랜덤 값을 단 한 번만 `closeTime`에 더하며, 이 랜덤 연장분을 목록/상세/대시보드 API와 SSE 어디에도 노출하지 않는다.

**Architecture:** 기존 `AuctionDeadlineScheduler`의 단일 롤링 타이머 패턴을 확장해 "다음 타겟"을 OPEN(ENDING 진입 시각)과 ENDING(실제 마감 시각) 중 더 이른 것으로 계산한다. 실제 전환 로직은 새 `AuctionEndingTransitionService`(`@Profile("!redis")`)에 모으고, 정밀 스케줄러와 60초 백업 폴러(`AuctionClosingScheduler`) 둘 다 이 서비스의 같은 멱등 메서드를 호출한다. 마스킹은 신규 필드 없이 기존 `estimatedCloseTime` 컬럼을 재활용한다 — OPEN 동안은 아무도 안 건드려서 자동으로 `closeTime`과 같고, ENDING 전환 시 `closeTime`만 랜덤 연장되고 `estimatedCloseTime`은 그대로 남아 자연스럽게 "얼림"이 된다.

**Tech Stack:** Java 21, Spring Boot 4.1, MySQL(JPA/Hibernate), JUnit 5, Mockito, AssertJ, TypeScript/React(Vite), Vitest

## Global Constraints

- 스코프는 `!redis` 프로필(DB 기반 입찰 경로)로 한정한다. `RedisBidExecutor`, `bid-accept.lua`, `Auction.applyStreamBid()`, `RedisAuctionCloseSchedulerProcessor`는 이번 계획에서 건드리지 않는다.
- 랜덤 연장 값은 1~2분(60~120초) 사이 **균등분포**, **경매당 정확히 1회**만 적용한다.
- 고객에게 노출되는 마감시각(`ends_at`/`endsAt`, REST·SSE 공통)은 ENDING 이후 실제 `closeTime`(랜덤 연장 반영값)을 절대 담지 않는다. 이미 종료된 경매의 `closedAt`류 시각은 예외로, 실제 값을 유지한다.
- `AuctionCloseScheduleChangedEvent`, `AuctionStreamPublisher`, 기존 두 스케줄러(`AuctionDeadlineScheduler`, `AuctionClosingScheduler`)의 기존 계약(반환 타입, 재시작 시 재스케줄, 백업 폴러 주기)은 유지한다 — 이번 변경은 그 위에 얹는다.
- 신규 필드·컬럼을 추가하지 않는다. 기존 `estimated_close_time` 컬럼을 그대로 재활용한다.
- 설계 문서: [`2-ending-status-random-extension-design.md`](2-ending-status-random-extension-design.md) (이슈 #418)

---

### Task 1: 도메인 규칙 — `Auction.enterEnding()` 추가, `placeBid()` 연장 로직 제거

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/domain/Auction.java`
- Modify: `backend/src/test/java/com/dbidding/auction/domain/AuctionTest.java`

**Interfaces:**
- Produces: `Auction.enterEnding(Duration randomExtension)` — `boolean` 반환(전환 성공 여부). `Auction.placeBid(Long bidPrice, Instant bidAt)` — 반환 타입이 `boolean`에서 `void`로 바뀜.

- [ ] **Step 1: 실패하는 테스트를 먼저 작성한다**

`AuctionTest.java`에 다음 테스트를 추가한다(기존에 "입찰마다 5분 연장"을 검증하던 테스트가 있다면 이 태스크에서 함께 삭제한다).

```java
@Test
void OPEN_경매가_ENDING으로_전환되면_closeTime만_랜덤_연장분만큼_늘고_estimatedCloseTime은_그대로다() {
    Instant closeTime = Instant.parse("2026-08-12T10:00:00Z");
    Auction auction = openAuction(closeTime);

    boolean transitioned = auction.enterEnding(Duration.ofSeconds(90));

    assertThat(transitioned).isTrue();
    assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ENDING);
    assertThat(auction.getCloseTime()).isEqualTo(closeTime.plusSeconds(90));
    assertThat(auction.getEstimatedCloseTime()).isEqualTo(closeTime);
}

@Test
void 이미_ENDING인_경매를_다시_전환해도_상태와_두_시각_모두_변하지_않는다() {
    Instant closeTime = Instant.parse("2026-08-12T10:00:00Z");
    Auction auction = openAuction(closeTime);
    auction.enterEnding(Duration.ofSeconds(60));
    Instant extendedCloseTime = auction.getCloseTime();

    boolean transitioned = auction.enterEnding(Duration.ofSeconds(90));

    assertThat(transitioned).isFalse();
    assertThat(auction.getCloseTime()).isEqualTo(extendedCloseTime);
    assertThat(auction.getEstimatedCloseTime()).isEqualTo(closeTime);
}

@Test
void ENDING_상태에서_일반_입찰은_현재가와_입찰수만_바꾸고_시각은_바꾸지_않는다() {
    Instant closeTime = Instant.parse("2026-08-12T10:00:00Z");
    Auction auction = openAuction(closeTime);
    auction.enterEnding(Duration.ofSeconds(60));
    Instant extendedCloseTime = auction.getCloseTime();

    auction.placeBid(50_000L, closeTime.minusSeconds(30));

    assertThat(auction.getCurrentPrice()).isEqualTo(50_000L);
    assertThat(auction.getBidCount()).isEqualTo(1);
    assertThat(auction.getCloseTime()).isEqualTo(extendedCloseTime);
    assertThat(auction.getEstimatedCloseTime()).isEqualTo(closeTime);
}

private Auction openAuction(Instant closeTime) {
    Auction auction = Auction.builder()
            .sellerId(1).itemId(1).auctionName("경매 A").description("설명")
            .startPrice(10_000L).deliveryFee(0L)
            .openTime(closeTime.minus(Duration.ofDays(1)))
            .estimatedCloseTime(closeTime).closeTime(closeTime)
            .bidPriceUnit(1_000L).hyped(false)
            .build();
    return auction;
}
```

- [ ] **Step 2: 테스트가 컴파일 실패로 죽는지 확인한다**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.domain.AuctionTest' 2>&1 | tail -30
```

Expected: `enterEnding`이 없어 컴파일 에러.

- [ ] **Step 3: `Auction.java`를 수정한다**

`extendCloseTimeIfNeeded` 메서드(171~191번 줄)를 지우고 다음으로 교체한다.

```java
public boolean enterEnding(Duration randomExtension) {
    if (status != AuctionStatus.OPEN) {
        return false;
    }
    closeTime = closeTime.plus(randomExtension);
    status = AuctionStatus.ENDING;
    return true;
}
```

`placeBid` 메서드(193~211번 줄)를 다음으로 교체한다 — 파라미터에서 `extensionWindow`/`extensionDuration`을 빼고, 반환 타입을 `void`로 바꾼다.

```java
public void placeBid(Long bidPrice, Instant bidAt) {
    if (status != AuctionStatus.OPEN && status != AuctionStatus.ENDING) {
        throw new IllegalArgumentException("진행 중인 경매에만 입찰할 수 있습니다.");
    }
    if (!bidAt.isBefore(closeTime)) {
        throw new IllegalArgumentException("이미 종료된 경매입니다.");
    }
    if (bidPrice < minimumBid()) {
        throw new IllegalArgumentException("최소 입찰가 이상으로 입찰해야 합니다.");
    }
    currentPrice = bidPrice;
    bidCount++;
}
```

`java.time.Duration` import는 이미 있으므로 그대로 둔다.

- [ ] **Step 4: 테스트를 다시 실행해 통과를 확인한다**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.domain.AuctionTest'
```

Expected: PASS. `placeBid`/`enterEnding` 관련 기존 테스트 중 옛 시그니처(`extensionWindow`/`extensionDuration` 인자, `boolean` 반환)를 쓰던 것들은 이 Step에서 같이 새 시그니처로 고친다.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/dbidding/auction/domain/Auction.java \
  backend/src/test/java/com/dbidding/auction/domain/AuctionTest.java
git commit -m "feat: Auction에 시간기준 ENDING 전환 추가, 입찰 연장 로직 제거"
```

---

### Task 2: 리포지토리 — 다음 타겟/백업폴러용 쿼리 추가

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/repository/AuctionRepository.java`

**Interfaces:**
- Produces: `findFirstOpenByCloseTimeAsc(): List<Auction>` (Pageable 1건), `findOverdueEndingCandidateIds(Instant threshold, Pageable): List<Integer>`
- Consumes: 기존 `findNextCloseTarget`(ENDING 전용으로 좁혀서 재사용), `findByIdForUpdate`

**Interfaces 상세:** `findNextCloseTarget`는 이후 태스크에서 `List.of(AuctionStatus.ENDING)`으로만 호출한다(시그니처는 안 바꾼다 — 호출부만 바뀐다).

- [ ] **Step 1: 메서드를 추가한다**

`AuctionRepository.java`의 `findNextCloseTarget` 바로 아래에 추가한다.

```java
@Query("""
        select a from Auction a
        where a.status = com.dbidding.auction.domain.AuctionStatus.OPEN
        order by a.closeTime asc, a.id asc
        """)
List<Auction> findFirstOpenByCloseTimeAsc(Pageable pageable);

@Query("""
        select a.id from Auction a
        where a.status = com.dbidding.auction.domain.AuctionStatus.OPEN
          and a.closeTime <= :threshold
        order by a.closeTime asc, a.id asc
        """)
List<Integer> findOverdueEndingCandidateIds(
        @Param("threshold") Instant threshold,
        Pageable pageable
);
```

이 파일은 순수 선언(JPQL)이라 별도 컴파일 스텝 없이 다음 태스크(스케줄러)에서 함께 검증된다.

- [ ] **Step 2: 컴파일 확인**

```bash
cd backend
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/dbidding/auction/repository/AuctionRepository.java
git commit -m "feat: ENDING 전환용 리포지토리 쿼리 추가"
```

---

### Task 3: `DbBidExecutor` — 입찰 연장 로직 제거, 응답 마감시각 마스킹

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/bid/DbBidExecutor.java`
- Modify: `backend/src/main/java/com/dbidding/auction/bid/BidEventData.java`
- Modify: `backend/src/test/java/com/dbidding/auction/bid/DbBidExecutorTest.java`

**Interfaces:**
- Consumes: Task 1의 `Auction.placeBid(Long, Instant)`(파라미터 2개로 축소), `Auction.getEstimatedCloseTime()`
- Produces: `BidEventData`에서 `closeTimeExtended` 필드 제거. `DbBidExecutor.bidResult()`가 만드는 `BidResponses.AuctionSnapshot.endsAt`이 `auction.getEstimatedCloseTime()`에서 옴.

- [ ] **Step 1: `DbBidExecutorTest.java`에서 옛 연장 관련 단언을 지운다**

`closeTimeExtended`/`BID_EXTENSION_WINDOW`/`BID_EXTENSION_DURATION`을 참조하는 테스트 메서드를 찾아(`grep -n "closeTimeExtended\|BID_EXTENSION" backend/src/test/java/com/dbidding/auction/bid/DbBidExecutorTest.java`) 다음 규칙으로 고친다.

- "마감 5분 이내 입찰 시 5분 연장됨"류 테스트는 삭제한다(도메인 계약이 Task 1에서 이미 바뀌었고, 이 계약은 `AuctionTest`가 검증한다).
- 응답의 `ends_at`을 검증하던 테스트가 있다면, ENDING 상태 fixture에서 `estimatedCloseTime != closeTime`이 되도록 만들고 `AuctionSnapshot.endsAt()`이 `estimatedCloseTime`과 같은지로 단언을 바꾼다.

새 테스트를 하나 추가한다.

```java
@Test
void ENDING_경매에_입찰하면_응답의_ends_at은_실제_closeTime이_아니라_얼린_estimatedCloseTime이다() {
    Instant estimatedCloseTime = Instant.parse("2026-08-12T10:00:00Z");
    Instant realCloseTime = estimatedCloseTime.plusSeconds(90);
    Auction auction = endingAuction(estimatedCloseTime, realCloseTime);
    when(auctionRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
    // (기존 이 테스트 클래스의 다른 성공 케이스와 동일한 방식으로 walletService/bidRepository 스텁 구성)

    BidExecutionResult result = executor.execute(new BidCommand(2, auction.getId(), 11_000L, "idem-key"));

    assertThat(result.result().auction().endsAt()).isEqualTo(estimatedCloseTime);
}

private Auction endingAuction(Instant estimatedCloseTime, Instant realCloseTime) {
    Auction auction = Auction.builder()
            .sellerId(1).itemId(1).auctionName("경매 A").description("설명")
            .startPrice(10_000L).deliveryFee(0L)
            .openTime(estimatedCloseTime.minus(Duration.ofDays(1)))
            .estimatedCloseTime(estimatedCloseTime).closeTime(estimatedCloseTime)
            .bidPriceUnit(1_000L).hyped(false)
            .build();
    auction.enterEnding(Duration.between(estimatedCloseTime, realCloseTime));
    return auction;
}
```

이 테스트 클래스의 기존 성공 케이스가 `walletService`/`bidRepository`를 어떻게 스텁하는지 그대로 따라간다(예: `findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc` 빈 결과, `walletService.hold(...)` 성공 응답).

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.bid.DbBidExecutorTest' 2>&1 | tail -40
```

Expected: `ends_at`이 여전히 `realCloseTime`이라 값 불일치로 FAIL(또는 옛 시그니처 컴파일 에러 — 둘 다 이 Step에서는 실패가 정상).

- [ ] **Step 3: `BidEventData.java`에서 `closeTimeExtended` 필드를 제거한다**

```java
public record BidEventData(
        Integer itemId,
        Integer previousBidderId,
        Long previousBidId,
        Long startPrice,
        Long bidIncrement,
        AuctionStatus status,
        AuctionCloseData closeData
) {
}
```

- [ ] **Step 4: `DbBidExecutor.java`를 수정한다**

`BID_EXTENSION_WINDOW`/`BID_EXTENSION_DURATION` 상수(49~50번 줄)를 삭제한다.

`execute()` 안에서 `boolean closeTimeExtended = placeBid(auction, bidPrice, bidAt);` 줄을 `placeBid(auction, bidPrice, bidAt);`(반환값 버림)로 바꾸고, 아래에 있던 `boolean scheduleExtended = closeTimeExtended && !buyNow;`와 그 값을 쓰는 로그 블록(`if (scheduleExtended) { ... }`)을 통째로 삭제한다. `closeTimeExtended`를 로그 메시지에 찍던 부분도 그 필드 참조를 지운다.

`placeBid` 헬퍼 호출부(있다면 `private boolean placeBid(...)` 래퍼)를 `Auction.placeBid(Long, Instant)` 새 시그니처에 맞게 인자 2개로 줄인다.

`BidEventData` 생성 부분에서 `scheduleExtended` 인자를 제거한다.

`bidResult()` 메서드의 `AuctionSnapshot` 생성 부분에서 `auction.getCloseTime()`을 `auction.getEstimatedCloseTime()`으로 바꾼다.

```java
new BidResponses.AuctionSnapshot(
        auction.getId(),
        auction.getCurrentPrice(),
        auction.minimumBid(),
        auction.getBidCount(),
        auction.getEstimatedCloseTime()
),
```

- [ ] **Step 5: 테스트를 다시 실행해 통과를 확인한다**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.bid.DbBidExecutorTest'
```

Expected: PASS.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/dbidding/auction/bid/DbBidExecutor.java \
  backend/src/main/java/com/dbidding/auction/bid/BidEventData.java \
  backend/src/test/java/com/dbidding/auction/bid/DbBidExecutorTest.java
git commit -m "refactor: 입찰 경로에서 마감연장 로직 제거, 응답 마감시각을 estimatedCloseTime으로 통일"
```

---

### Task 4: `AuctionCommandService`/`AuctionQueryService` — 나머지 `ends_at` 소스 교체

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/service/AuctionCommandService.java`
- Modify: `backend/src/main/java/com/dbidding/auction/service/AuctionQueryService.java`
- Modify: `backend/src/test/java/com/dbidding/auction/service/AuctionQueryServiceTest.java` (있는 만큼)

**Interfaces:**
- Consumes: `Auction.getEstimatedCloseTime()`

**변경 대상 정확한 위치(DB 경로만, redis 분기는 그대로 둔다):**

| 파일 | 현재 | 변경 |
|---|---|---|
| `AuctionCommandService.java` — `create()`의 `AuctionOpenedEvent` 생성 | `savedAuction.getCloseTime()` | `savedAuction.getEstimatedCloseTime()` |
| `AuctionCommandService.java` — `publishBidEvents()`의 `if (data.closeTimeExtended())` 블록 전체 | (Task 3에서 `closeTimeExtended` 필드 자체가 없어졌으므로) | 이 블록을 통째로 삭제 |
| `AuctionCommandService.java` — `createResponse()` | `.endsAt(auction.getCloseTime())` | `.endsAt(auction.getEstimatedCloseTime())` |
| `AuctionQueryService.java` — `summary()`(DB 경로) | `.endsAt(realtime == null ? auction.getCloseTime() : realtime.closeTime())` | `.endsAt(realtime == null ? auction.getEstimatedCloseTime() : realtime.closeTime())` |
| `AuctionQueryService.java` — `detail(Auction, ...)`(DB 경로, redis 아닌 오버로드) | `.endsAt(auction.getCloseTime())` | `.endsAt(auction.getEstimatedCloseTime())` |

**바꾸지 않는 곳(의도적으로 실제 값 유지):** `closeResponse()`, `publishAuctionClosed()`의 `AuctionClosedEvent`, `getFailedAuctions()` — 전부 이미 종료/실패한 경매의 확정 시각이라 마스킹 대상이 아니다(설계 문서 9장). redis 분기(`createInRedis`, `redisSummary`, `redisDetail`, realtime 기반 `detail`)도 이번 스코프 밖이라 그대로 둔다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`AuctionQueryServiceTest.java`에 추가한다(생성자 의존성 mock은 이 클래스의 기존 테스트와 동일하게 구성).

```java
@Test
void ENDING_경매_목록_조회의_ends_at은_실제_closeTime이_아니라_얼린_estimatedCloseTime이다() {
    Instant estimatedCloseTime = Instant.parse("2026-08-12T10:00:00Z");
    Auction auction = endingAuction(estimatedCloseTime, estimatedCloseTime.plusSeconds(90));
    // 기존 search() 테스트와 동일하게 auctionRepository.searchByCursor(...) 등을 스텁해
    // content 목록에 auction이 포함되게 한다.

    AuctionResponses.CursorPage<AuctionResponses.AuctionSummary> page =
            queryService.search(null, defaultSearchRequest());

    assertThat(page.items()).extracting(AuctionResponses.AuctionSummary::endsAt)
            .containsExactly(estimatedCloseTime);
}

@Test
void ENDING_경매_상세_조회의_ends_at도_얼린_estimatedCloseTime이다() {
    Instant estimatedCloseTime = Instant.parse("2026-08-12T10:00:00Z");
    Auction auction = endingAuction(estimatedCloseTime, estimatedCloseTime.plusSeconds(90));
    when(auctionRepository.findById(auction.getId())).thenReturn(Optional.of(auction));
    // 기존 getDetail() 테스트와 동일하게 cardService/auctionImageRepository 스텁 구성

    AuctionResponses.AuctionDetail detail = queryService.getDetail(null, auction.getId());

    assertThat(detail.endsAt()).isEqualTo(estimatedCloseTime);
}
```

`endingAuction(...)` 헬퍼는 Task 3에서 `DbBidExecutorTest`에 추가한 것과 같은 모양으로 이 테스트 클래스에도 추가한다(빌더로 OPEN 생성 후 `enterEnding(...)` 호출).

- [ ] **Step 2: 실패 확인**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.service.AuctionQueryServiceTest' 2>&1 | tail -40
```

Expected: `endsAt`이 실제 `closeTime`으로 나와 값 불일치 FAIL.

- [ ] **Step 3: 위 표대로 두 서비스 파일을 수정한다**

- [ ] **Step 4: 테스트 재실행**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.service.AuctionQueryServiceTest' \
  --tests 'com.dbidding.auction.service.AuctionCommandServiceTest'
```

Expected: PASS. `AuctionCommandServiceTest`에 `closeTimeExtended`/`AuctionCloseScheduleChangedEvent("close_time_extended")` 발행을 검증하던 테스트가 있으면 삭제한다(그 경로가 이제 없다).

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/dbidding/auction/service/AuctionCommandService.java \
  backend/src/main/java/com/dbidding/auction/service/AuctionQueryService.java \
  backend/src/test/java/com/dbidding/auction/service/AuctionQueryServiceTest.java \
  backend/src/test/java/com/dbidding/auction/service/AuctionCommandServiceTest.java
git commit -m "fix: 경매 목록/상세/생성 응답의 ends_at을 estimatedCloseTime으로 통일"
```

---

### Task 5: `AuctionMetrics` — ENDING 전환 카운터 추가

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/metrics/AuctionMetrics.java`
- Modify: `backend/src/test/java/com/dbidding/auction/metrics/AuctionMetricsTest.java` (있으면, 없으면 새로 만들 필요 없이 아래 검증만 통과시킨다)

**Interfaces:**
- Produces: `AuctionMetrics.recordEndingTransition()`

- [ ] **Step 1: 필드·초기화·메서드를 추가한다**

```java
private final Counter auctionEndingTransitions;
```

생성자의 `auctionExtensions = Counter.builder(...)` 바로 아래에 추가한다.

```java
auctionEndingTransitions = Counter.builder("dbidding.auction.ending.transitions")
        .description("경매가 시간 기준으로 ENDING 상태에 진입한 횟수(단발 랜덤 연장 적용 시점)")
        .register(registry);
```

`recordExtension()` 메서드 아래에 추가한다.

```java
public void recordEndingTransition() {
    auctionEndingTransitions.increment();
}
```

- [ ] **Step 2: 컴파일 및 전체 auction 패키지 테스트로 회귀 확인**

```bash
cd backend
./gradlew compileJava
./gradlew test --tests 'com.dbidding.auction.metrics.*'
```

Expected: BUILD SUCCESSFUL / PASS(기존 테스트에 영향 없음 — 새 카운터를 아무도 아직 호출하지 않는 시점).

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/dbidding/auction/metrics/AuctionMetrics.java
git commit -m "feat: ENDING 전환 카운터(dbidding.auction.ending.transitions) 추가"
```

---

### Task 6: SSE — `AUCTION_ENDING_STARTED` 이벤트 타입/페이로드 추가

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/sse/AuctionStreamEventType.java`
- Modify: `backend/src/main/java/com/dbidding/auction/sse/AuctionStreamPayload.java`
- Create: `backend/src/test/java/com/dbidding/auction/sse/AuctionStreamPayloadTest.java`(없으면 새로 생성)

**Interfaces:**
- Consumes: `Auction`(도메인 엔티티) — `getId()`, `getStartPrice()`, `getCurrentPrice()`, `getBidPriceUnit()`, `getBidCount()`, `getEstimatedCloseTime()`, `getStatus()`
- Produces: `AuctionStreamPayload.endingStarted(Auction auction, Instant occurredAt): AuctionStreamPayload`

- [ ] **Step 1: 실패하는 테스트를 작성한다**

```java
package com.dbidding.auction.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuctionStreamPayloadTest {
    @Test
    void endingStarted_페이로드는_얼린_estimatedCloseTime을_ends_at으로_담고_진짜_closeTime은_안_담는다() {
        Instant estimatedCloseTime = Instant.parse("2026-08-12T10:00:00Z");
        Instant realCloseTime = estimatedCloseTime.plusSeconds(90);
        Auction auction = Auction.builder()
                .sellerId(1).itemId(1).auctionName("경매 A").description("설명")
                .startPrice(10_000L).deliveryFee(0L)
                .openTime(estimatedCloseTime.minus(Duration.ofDays(1)))
                .estimatedCloseTime(estimatedCloseTime).closeTime(estimatedCloseTime)
                .bidPriceUnit(1_000L).hyped(false)
                .build();
        auction.enterEnding(Duration.between(estimatedCloseTime, realCloseTime));

        AuctionStreamPayload payload = AuctionStreamPayload.endingStarted(auction, realCloseTime);

        assertThat(payload.type()).isEqualTo(AuctionStreamEventType.AUCTION_ENDING_STARTED);
        assertThat(payload.auctionId()).isEqualTo(auction.getId());
        assertThat(payload.status()).isEqualTo(AuctionStatus.ENDING);
        assertThat(payload.endsAt()).isEqualTo(estimatedCloseTime);
        assertThat(payload.endsAt()).isNotEqualTo(auction.getCloseTime());
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.sse.AuctionStreamPayloadTest' 2>&1 | tail -30
```

Expected: `endingStarted` 메서드가 없어 컴파일 에러.

- [ ] **Step 3: `AuctionStreamEventType.java`에 상수를 추가한다**

```java
package com.dbidding.auction.sse;

public enum AuctionStreamEventType {
    AUCTION_CREATED,
    BID_PLACED,
    AUCTION_CLOSED,
    AUCTION_ENDING_STARTED
}
```

- [ ] **Step 4: `AuctionStreamPayload.java`에 팩토리 메서드를 추가한다**

`closed(...)` 메서드 바로 아래에 추가한다(import에 `com.dbidding.auction.domain.Auction` 추가 필요).

```java
public static AuctionStreamPayload endingStarted(Auction auction, Instant occurredAt) {
    return new AuctionStreamPayload(
            AuctionStreamEventType.AUCTION_ENDING_STARTED, auction.getId(), null, null, null, null, null, null,
            null, null, null, auction.getStartPrice(), auction.getCurrentPrice(), null,
            auction.getBidPriceUnit(), auction.getBidCount(), auction.getEstimatedCloseTime(),
            auction.getStatus(), null, occurredAt, null);
}
```

- [ ] **Step 5: 테스트 재실행**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.sse.AuctionStreamPayloadTest'
```

Expected: PASS.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/dbidding/auction/sse/AuctionStreamEventType.java \
  backend/src/main/java/com/dbidding/auction/sse/AuctionStreamPayload.java \
  backend/src/test/java/com/dbidding/auction/sse/AuctionStreamPayloadTest.java
git commit -m "feat: SSE AUCTION_ENDING_STARTED 이벤트 타입/페이로드 추가"
```

---

### Task 7: 랜덤 연장값 공급자 — `EndingExtensionProvider`

**Files:**
- Create: `backend/src/main/java/com/dbidding/auction/service/EndingExtensionProvider.java`
- Create: `backend/src/main/java/com/dbidding/auction/service/RandomEndingExtensionProvider.java`
- Create: `backend/src/test/java/com/dbidding/auction/service/RandomEndingExtensionProviderTest.java`

**Interfaces:**
- Produces: `EndingExtensionProvider.next(): Duration` — 1~2분(60~120초, 양끝 포함) 균등분포. `RandomEndingExtensionProvider`(`@Component`, `@Profile("!redis")`)가 기본 구현.
- 테스트 전용으로 Task 8·9에서 이 인터페이스를 고정값 반환 페이크로 교체해 쓴다(운영 빈은 이 태스크에서만 등록).

- [ ] **Step 1: 인터페이스를 만든다**

```java
package com.dbidding.auction.service;

import java.time.Duration;

public interface EndingExtensionProvider {
    Duration next();
}
```

- [ ] **Step 2: 실패하는 테스트를 작성한다**

```java
package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RandomEndingExtensionProviderTest {
    private final RandomEndingExtensionProvider provider = new RandomEndingExtensionProvider();

    @Test
    void 매번_60초에서_120초_사이_값을_돌려준다() {
        IntStream.range(0, 200).forEach(i -> {
            Duration extension = provider.next();
            assertThat(extension).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(120));
        });
    }
}
```

- [ ] **Step 3: 실패 확인**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.service.RandomEndingExtensionProviderTest' 2>&1 | tail -20
```

Expected: 클래스 없어 컴파일 에러.

- [ ] **Step 4: 구현한다**

```java
package com.dbidding.auction.service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!redis")
public class RandomEndingExtensionProvider implements EndingExtensionProvider {
    private static final long MIN_SECONDS = 60;
    private static final long MAX_SECONDS = 120;

    @Override
    public Duration next() {
        return Duration.ofSeconds(ThreadLocalRandom.current().nextLong(MIN_SECONDS, MAX_SECONDS + 1));
    }
}
```

- [ ] **Step 5: 테스트 재실행**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.service.RandomEndingExtensionProviderTest'
```

Expected: PASS.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/dbidding/auction/service/EndingExtensionProvider.java \
  backend/src/main/java/com/dbidding/auction/service/RandomEndingExtensionProvider.java \
  backend/src/test/java/com/dbidding/auction/service/RandomEndingExtensionProviderTest.java
git commit -m "feat: 1~2분 균등분포 랜덤 연장값 공급자 추가"
```

---

### Task 8: `AuctionEndingTransitionService` — 전환 핵심 로직(멱등)

**Files:**
- Create: `backend/src/main/java/com/dbidding/auction/service/AuctionEndingTransitionService.java`
- Create: `backend/src/test/java/com/dbidding/auction/service/AuctionEndingTransitionServiceTest.java`

**Interfaces:**
- Consumes: `AuctionRepository.findByIdForUpdate`, `Auction.enterEnding(Duration)`, `EndingExtensionProvider.next()`, `AuctionMetrics.recordEndingTransition()`, `AuctionStreamPublisher.publish(AuctionStreamPayload)`, `ApplicationEventPublisher`, `AuctionCloseScheduleChangedEvent`
- Produces: `AuctionEndingTransitionService.transitionIfDue(Integer auctionId, Instant now): boolean` — Task 9(정밀 스케줄러)와 Task 10(백업 폴러)이 **같은 메서드**를 호출한다. 아직 5분 전이 안 됐거나, 이미 OPEN이 아니거나, 대상이 없으면 아무 것도 안 하고 `false`를 돌려준다.

**동작 규칙(설계 문서 3장):** ENDING 진입 윈도우는 5분으로 고정한다(`Duration.ofMinutes(5)`), `Auction`엔 이 상수를 안 두고 이 서비스에만 둔다 — 도메인은 "언제 몇 초 연장할지" 계산 없이 "지금 전환해라"만 받는다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

```java
package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.sse.AuctionStreamPayload;
import com.dbidding.auction.sse.AuctionStreamPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AuctionEndingTransitionServiceTest {
    private final AuctionRepository auctionRepository = mock(AuctionRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AuctionStreamPublisher auctionStreamPublisher = mock(AuctionStreamPublisher.class);
    private final AuctionMetrics auctionMetrics = mock(AuctionMetrics.class);
    private final EndingExtensionProvider extensionProvider = mock(EndingExtensionProvider.class);
    private final AuctionEndingTransitionService service = new AuctionEndingTransitionService(
            auctionRepository, eventPublisher, auctionStreamPublisher, auctionMetrics, extensionProvider
    );

    @Test
    void 남은시간이_5분_이하인_OPEN_경매는_ENDING으로_전환되고_랜덤연장과_이벤트가_한번씩_나간다() {
        Instant closeTime = Instant.parse("2026-08-12T10:00:00Z");
        Instant now = closeTime.minusSeconds(30);
        Auction auction = openAuction(1, closeTime);
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(extensionProvider.next()).thenReturn(Duration.ofSeconds(90));

        boolean transitioned = service.transitionIfDue(1, now);

        assertThat(transitioned).isTrue();
        assertThat(auction.getCloseTime()).isEqualTo(closeTime.plusSeconds(90));
        assertThat(auction.getEstimatedCloseTime()).isEqualTo(closeTime);
        verify(auctionMetrics, times(1)).recordEndingTransition();
        verify(auctionStreamPublisher, times(1)).publish(any(AuctionStreamPayload.class));
        verify(eventPublisher, times(1)).publishEvent(any(AuctionCloseScheduleChangedEvent.class));
    }

    @Test
    void 아직_5분_전이_안된_OPEN_경매는_전환하지_않는다() {
        Instant closeTime = Instant.parse("2026-08-12T10:00:00Z");
        Instant now = closeTime.minus(Duration.ofMinutes(10));
        Auction auction = openAuction(1, closeTime);
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));

        boolean transitioned = service.transitionIfDue(1, now);

        assertThat(transitioned).isFalse();
        assertThat(auction.getCloseTime()).isEqualTo(closeTime);
        verify(auctionMetrics, never()).recordEndingTransition();
    }

    @Test
    void 이미_ENDING인_경매는_다시_전환하지_않는다() {
        Instant closeTime = Instant.parse("2026-08-12T10:00:00Z");
        Auction auction = openAuction(1, closeTime);
        auction.enterEnding(Duration.ofSeconds(60));
        Instant extendedCloseTime = auction.getCloseTime();
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));

        boolean transitioned = service.transitionIfDue(1, closeTime.minusSeconds(30));

        assertThat(transitioned).isFalse();
        assertThat(auction.getCloseTime()).isEqualTo(extendedCloseTime);
        verify(auctionMetrics, never()).recordEndingTransition();
    }

    @Test
    void 대상_경매가_없으면_아무_일도_안_한다() {
        when(auctionRepository.findByIdForUpdate(99)).thenReturn(Optional.empty());

        boolean transitioned = service.transitionIfDue(99, Instant.now());

        assertThat(transitioned).isFalse();
        verify(auctionStreamPublisher, never()).publish(any());
    }

    private Auction openAuction(Integer id, Instant closeTime) {
        Auction auction = Auction.builder()
                .sellerId(1).itemId(1).auctionName("경매 A").description("설명")
                .startPrice(10_000L).deliveryFee(0L)
                .openTime(closeTime.minus(Duration.ofDays(1)))
                .estimatedCloseTime(closeTime).closeTime(closeTime)
                .bidPriceUnit(1_000L).hyped(false)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(auction, "id", id);
        return auction;
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.service.AuctionEndingTransitionServiceTest' 2>&1 | tail -30
```

Expected: 클래스 없어 컴파일 에러.

- [ ] **Step 3: 구현한다**

```java
package com.dbidding.auction.service;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.sse.AuctionStreamPayload;
import com.dbidding.auction.sse.AuctionStreamPublisher;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Profile("!redis")
@RequiredArgsConstructor
public class AuctionEndingTransitionService {
    private static final Duration ENDING_WINDOW = Duration.ofMinutes(5);

    private final AuctionRepository auctionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuctionStreamPublisher auctionStreamPublisher;
    private final AuctionMetrics auctionMetrics;
    private final EndingExtensionProvider extensionProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean transitionIfDue(Integer auctionId, Instant now) {
        return auctionRepository.findByIdForUpdate(auctionId)
                .filter(auction -> auction.getStatus() == AuctionStatus.OPEN)
                .filter(auction -> !auction.getCloseTime().minus(ENDING_WINDOW).isAfter(now))
                .map(auction -> transition(auction, now))
                .orElse(false);
    }

    private boolean transition(Auction auction, Instant now) {
        Duration randomExtension = extensionProvider.next();
        boolean transitioned = auction.enterEnding(randomExtension);
        if (!transitioned) {
            return false;
        }
        auctionMetrics.recordEndingTransition();
        log.info(
                "event=auction.ending.transitioned auctionId={} estimatedCloseTime={} realCloseTime={} extensionSeconds={}",
                auction.getId(), auction.getEstimatedCloseTime(), auction.getCloseTime(), randomExtension.toSeconds()
        );
        eventPublisher.publishEvent(new AuctionCloseScheduleChangedEvent(
                auction.getId(), auction.getCloseTime(), "ending_transition"
        ));
        auctionStreamPublisher.publish(AuctionStreamPayload.endingStarted(auction, now));
        return true;
    }
}
```

`@Transactional(propagation = REQUIRES_NEW)`로 둔 이유: 정밀 스케줄러(Task 9)의 콜백과 백업 폴러(Task 10)의 반복 루프 양쪽에서 각 경매를 독립된 트랜잭션으로 처리해야, 한 경매 처리 실패가 같은 배치의 다른 경매나 바깥 트랜잭션에 번지지 않는다(기존 `AuctionCommandService.closeDueAuction()`도 같은 이유로 `REQUIRES_NEW`를 쓴다).

- [ ] **Step 4: 테스트 재실행**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.service.AuctionEndingTransitionServiceTest'
```

Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/dbidding/auction/service/AuctionEndingTransitionService.java \
  backend/src/test/java/com/dbidding/auction/service/AuctionEndingTransitionServiceTest.java
git commit -m "feat: ENDING 전환 핵심 로직(멱등, REQUIRES_NEW) 추가"
```

---

### Task 9: `AuctionDeadlineScheduler` — 다음 타겟 계산을 OPEN/ENDING 이원화

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/service/AuctionDeadlineScheduler.java`
- Modify: `backend/src/test/java/com/dbidding/auction/service/AuctionDeadlineSchedulerTest.java`

**Interfaces:**
- Consumes: `AuctionRepository.findFirstOpenByCloseTimeAsc(Pageable)`(Task 2), `AuctionEndingTransitionService.transitionIfDue(Integer, Instant)`(Task 8)
- Produces: `scheduleNext(String reason)`, `reschedule(AuctionCloseScheduleChangedEvent)` 기존 공개 계약 유지. `ENDING_WINDOW = Duration.ofMinutes(5)`를 이 클래스에도 상수로 둔다(다음 타겟 계산에 필요).

**설계:** `scheduleNext()`가 "OPEN 중 가장 이른 것의 `closeTime - 5분`"과 "ENDING 중 가장 이른 것의 `closeTime`"을 각각 조회해 더 이른 쪽을 타이머 타겟으로 고른다(설계 문서 10장에서 확정한 방식). 콜백이 발동하면 **기존과 동일하게** `processDueAuctions`를 먼저 호출해 실제 마감을 처리하고, **그 다음** 발동을 유발한 `scheduledAuctionId`에 대해 `transitionIfDue`를 호출해 ENDING 전환이 필요하면 처리한다 — 둘 다 멱등이라 순서를 바꿔도 안전하지만, 실제 마감이 우선순위가 높으므로 먼저 시도한다.

- [ ] **Step 1: 기존 테스트를 새 리포지토리 메서드에 맞게 고친다**

`AuctionDeadlineSchedulerTest.java`의 5개 테스트가 전부 `auctionRepository.findNextCloseTarget(List.of(OPEN, ENDING), ...)`를 스텁한다. 이걸 아래처럼 바꾼다 — **OPEN 후보가 없다고 스텁**하고 **ENDING 후보로 기존 `auction(...)`을 스텁**하면, 기존 테스트가 검증하던 "ENDING/마감" 경로 그대로를 최소 수정으로 재현할 수 있다.

```java
// 예: 첫 번째 테스트("가장_가까운_마감_시간에_맞춰_종료_작업을_예약한다")
when(auctionRepository.findFirstOpenByCloseTimeAsc(PageRequest.of(0, 1)))
        .thenReturn(List.of());
when(auctionRepository.findNextCloseTarget(
        List.of(AuctionStatus.ENDING),
        PageRequest.of(0, 1)
)).thenReturn(List.of(auction));
```

5개 테스트 전부 같은 패턴(“OPEN 없음 + ENDING 후보로 기존 fixture” 아니면 “OPEN 후보로 기존 fixture + ENDING 없음” 둘 중 시나리오에 맞는 쪽)으로 고친다.

클래스 상단의 필드 선언도 새 생성자 시그니처에 맞게 고친다.

```java
private final AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor = mock(AuctionCloseSchedulerProcessor.class);
private final AuctionRepository auctionRepository = mock(AuctionRepository.class);
private final AuctionEndingTransitionService auctionEndingTransitionService = mock(AuctionEndingTransitionService.class);
private final CapturingTaskScheduler taskScheduler = new CapturingTaskScheduler();
private final Clock clock = Clock.fixed(
        Instant.parse("2026-07-29T01:00:00Z"),
        ZoneId.of("Asia/Seoul")
);
private final AuctionDeadlineScheduler scheduler = new AuctionDeadlineScheduler(
        auctionCloseSchedulerProcessor,
        auctionRepository,
        auctionEndingTransitionService,
        taskScheduler,
        clock
);
```

새 테스트 3개를 추가한다.

```java
@Test
void OPEN_경매의_ENDING_진입_시각이_ENDING_경매의_마감보다_이르면_그쪽을_예약한다() {
    Auction openSoon = auction(1, Instant.parse("2026-07-29T01:10:00Z"));
    Auction endingLater = auction(2, Instant.parse("2026-07-29T02:00:00Z"));
    when(auctionRepository.findFirstOpenByCloseTimeAsc(PageRequest.of(0, 1)))
            .thenReturn(List.of(openSoon));
    when(auctionRepository.findNextCloseTarget(List.of(AuctionStatus.ENDING), PageRequest.of(0, 1)))
            .thenReturn(List.of(endingLater));

    scheduler.scheduleNext("test");

    assertThat(taskScheduler.scheduledInstant)
            .isEqualTo(Instant.parse("2026-07-29T01:10:00Z").minus(Duration.ofMinutes(5)));
}

@Test
void 콜백_실행_시_실제_마감_처리와_ENDING_전환_후보_모두_시도한다() {
    Auction openSoon = auction(1, Instant.parse("2026-07-29T01:10:00Z"));
    when(auctionRepository.findFirstOpenByCloseTimeAsc(PageRequest.of(0, 1)))
            .thenReturn(List.of(openSoon), List.of());
    when(auctionRepository.findNextCloseTarget(List.of(AuctionStatus.ENDING), PageRequest.of(0, 1)))
            .thenReturn(List.of());
    Instant fireTime = Instant.parse("2026-07-29T01:05:00Z");
    when(auctionCloseSchedulerProcessor.processDueAuctions(fireTime, 100)).thenReturn(List.of());
    when(auctionEndingTransitionService.transitionIfDue(1, fireTime)).thenReturn(true);

    scheduler.scheduleNext("test");
    taskScheduler.scheduledTask.run();

    verify(auctionCloseSchedulerProcessor).processDueAuctions(fireTime, 100);
    verify(auctionEndingTransitionService).transitionIfDue(1, fireTime);
}

@Test
void 두_후보가_모두_없으면_기존_예약을_취소하고_아무것도_안_한다() {
    when(auctionRepository.findFirstOpenByCloseTimeAsc(PageRequest.of(0, 1))).thenReturn(List.of());
    when(auctionRepository.findNextCloseTarget(List.of(AuctionStatus.ENDING), PageRequest.of(0, 1)))
            .thenReturn(List.of());

    scheduler.scheduleNext("test");

    assertThat(taskScheduler.scheduledTask).isNull();
}
```

`clock`은 이 테스트 클래스에 이미 `Instant.parse("2026-07-29T01:00:00Z")`로 고정돼 있으므로 `fireTime`은 콜백 안에서 `clock.instant()`를 부르는 지점과 일치시킨다(위 두 번째 테스트는 콜백이 `clock.instant()`를 다시 부르지 않고 스케줄된 시각을 그대로 쓰는 기존 구현을 그대로 따른다 — Step 2에서 실제 구현을 보고 필요하면 `fireTime` 값을 `clock.instant()`가 아니라 `scheduledCloseTime`으로 맞춘다).

- [ ] **Step 2: 실패 확인**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.service.AuctionDeadlineSchedulerTest' 2>&1 | tail -50
```

Expected: 새 생성자 인자·리포지토리 메서드가 없어 컴파일 에러.

- [ ] **Step 3: `AuctionDeadlineScheduler.java`를 수정한다**

생성자에 의존성을 추가한다.

```java
private static final Duration ENDING_WINDOW = Duration.ofMinutes(5);

private final AuctionEndingTransitionService auctionEndingTransitionService;

public AuctionDeadlineScheduler(
        AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor,
        AuctionRepository auctionRepository,
        AuctionEndingTransitionService auctionEndingTransitionService,
        @Qualifier("auctionDeadlineTaskScheduler") TaskScheduler taskScheduler,
        Clock clock
) {
    this.auctionCloseSchedulerProcessor = auctionCloseSchedulerProcessor;
    this.auctionRepository = auctionRepository;
    this.auctionEndingTransitionService = auctionEndingTransitionService;
    this.taskScheduler = taskScheduler;
    this.clock = clock;
}
```

`scheduleNext(String reason)`을 다음으로 교체한다.

```java
void scheduleNext(String reason) {
    synchronized (scheduleLock) {
        NextTarget target = resolveNextTarget();
        cancelScheduledTask();
        if (target == null) {
            scheduledAuctionId = null;
            scheduledCloseTime = null;
            log.info("event=auction.close.deadline.unscheduled reason={} target=none", reason);
            return;
        }
        scheduledAuctionId = target.auctionId();
        scheduledCloseTime = target.instant();
        scheduledTask = taskScheduler.schedule(this::closeDueAuctionsAtDeadline, scheduledCloseTime);
        log.info(
                "event=auction.close.deadline.scheduled auctionId={} instant={} reason={}",
                scheduledAuctionId, scheduledCloseTime, reason
        );
    }
}

private NextTarget resolveNextTarget() {
    List<Auction> openCandidates = auctionRepository.findFirstOpenByCloseTimeAsc(PageRequest.of(0, 1));
    List<Auction> endingCandidates = auctionRepository.findNextCloseTarget(
            List.of(AuctionStatus.ENDING), PageRequest.of(0, 1)
    );
    NextTarget openTarget = openCandidates.isEmpty() ? null
            : new NextTarget(openCandidates.get(0).getId(), openCandidates.get(0).getCloseTime().minus(ENDING_WINDOW));
    NextTarget endingTarget = endingCandidates.isEmpty() ? null
            : new NextTarget(endingCandidates.get(0).getId(), endingCandidates.get(0).getCloseTime());
    if (openTarget == null) {
        return endingTarget;
    }
    if (endingTarget == null) {
        return openTarget;
    }
    return openTarget.instant().isBefore(endingTarget.instant()) ? openTarget : endingTarget;
}

private record NextTarget(Integer auctionId, Instant instant) {
}
```

`closeDueAuctionsAtDeadline()`을 다음으로 교체한다 — 실제 마감 처리는 그대로 두고, 그 뒤에 ENDING 전환 시도를 추가한다.

```java
private void closeDueAuctionsAtDeadline() {
    Instant now = clock.instant();
    Integer firedAuctionId = scheduledAuctionId;
    log.info(
            "event=auction.close.deadline.triggered scheduledAuctionId={} scheduledCloseTime={} now={} batchSize={}",
            scheduledAuctionId, scheduledCloseTime, now, CLOSE_BATCH_SIZE
    );
    try {
        var closedAuctions = auctionCloseSchedulerProcessor.processDueAuctions(now, CLOSE_BATCH_SIZE);
        log.info(
                "event=auction.close.deadline.completed closedCount={} auctionIds={}",
                closedAuctions.size(), closedAuctions
        );
        if (firedAuctionId != null) {
            auctionEndingTransitionService.transitionIfDue(firedAuctionId, now);
        }
    } catch (RuntimeException exception) {
        log.error(
                "event=auction.close.deadline.failed scheduledAuctionId={} scheduledCloseTime={} now={} batchSize={}",
                scheduledAuctionId, scheduledCloseTime, now, CLOSE_BATCH_SIZE, exception
        );
        throw exception;
    } finally {
        scheduleNext("deadline_executed");
    }
}
```

`PageRequest`/`AuctionStatus`/`List` import는 이미 있으므로 그대로 둔다.

- [ ] **Step 4: 두 번째 새 테스트의 `fireTime` 값을 실제 구현에 맞춘다**

Step 3 구현은 `clock.instant()`를 다시 불러 `now`를 얻으므로, 테스트의 `clock`이 고정값(`2026-07-29T01:00:00Z`)이면 `fireTime`도 그 고정값과 같아야 한다. Step 1에서 작성한 두 번째 새 테스트의 `fireTime` 변수를 `Instant.parse("2026-07-29T01:00:00Z")`(테스트 클래스의 `clock` 고정값)로 맞춘다.

- [ ] **Step 5: 테스트 재실행**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.service.AuctionDeadlineSchedulerTest'
```

Expected: PASS(5개 기존 + 3개 신규 전부).

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/dbidding/auction/service/AuctionDeadlineScheduler.java \
  backend/src/test/java/com/dbidding/auction/service/AuctionDeadlineSchedulerTest.java
git commit -m "feat: 정밀 스케줄러가 ENDING 진입 시각도 다음 타겟으로 고려하게 확장"
```

---

### Task 10: `AuctionClosingScheduler` — 백업 폴러에 ENDING 전환 안전망 추가

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/service/AuctionClosingScheduler.java`
- Create: `backend/src/test/java/com/dbidding/auction/service/AuctionClosingSchedulerTest.java`(없으면 새로 생성 — 있으면 아래 테스트를 추가)

**Interfaces:**
- Consumes: `AuctionRepository.findOverdueEndingCandidateIds(Instant, Pageable)`(Task 2), `AuctionEndingTransitionService.transitionIfDue`(Task 8, `Optional`로 주입 — redis 프로필에서 빈이 없어도 스케줄러 자체는 살아있어야 하므로)

- [ ] **Step 1: 실패하는 테스트를 작성한다**

```java
package com.dbidding.auction.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class AuctionClosingSchedulerTest {
    private final AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor = mock(AuctionCloseSchedulerProcessor.class);
    private final AuctionRepository auctionRepository = mock(AuctionRepository.class);
    private final AuctionEndingTransitionService auctionEndingTransitionService = mock(AuctionEndingTransitionService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-12T10:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final AuctionClosingScheduler scheduler = new AuctionClosingScheduler(
            auctionCloseSchedulerProcessor, auctionRepository, Optional.of(auctionEndingTransitionService), clock
    );

    @Test
    void ENDING_진입_시각이_지난_OPEN_경매_후보를_모두_전환_시도한다() {
        Instant now = Instant.parse("2026-08-12T10:00:00Z");
        Instant threshold = now.plus(Duration.ofMinutes(5));
        when(auctionRepository.findOverdueEndingCandidateIds(threshold, PageRequest.of(0, 100)))
                .thenReturn(List.of(1, 2));

        scheduler.transitionOverdueEndingAuctions();

        verify(auctionEndingTransitionService).transitionIfDue(1, now);
        verify(auctionEndingTransitionService).transitionIfDue(2, now);
    }

    @Test
    void 후보가_없으면_전환을_시도하지_않는다() {
        when(auctionRepository.findOverdueEndingCandidateIds(any(), any())).thenReturn(List.of());

        scheduler.transitionOverdueEndingAuctions();

        verify(auctionEndingTransitionService, never()).transitionIfDue(any(), any());
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.service.AuctionClosingSchedulerTest' 2>&1 | tail -40
```

Expected: 새 생성자 시그니처·메서드가 없어 컴파일 에러.

- [ ] **Step 3: `AuctionClosingScheduler.java`를 수정한다**

```java
package com.dbidding.auction.service;

import com.dbidding.auction.repository.AuctionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "auction.closing.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AuctionClosingScheduler {
    private static final int CLOSE_BATCH_SIZE = 100;
    private static final Duration ENDING_WINDOW = Duration.ofMinutes(5);

    private final AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor;
    private final AuctionRepository auctionRepository;
    private final Optional<AuctionEndingTransitionService> auctionEndingTransitionService;
    private final Clock clock;

    public AuctionClosingScheduler(
            AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor,
            AuctionRepository auctionRepository,
            @Autowired(required = false) Optional<AuctionEndingTransitionService> auctionEndingTransitionService,
            Clock clock
    ) {
        this.auctionCloseSchedulerProcessor = auctionCloseSchedulerProcessor;
        this.auctionRepository = auctionRepository;
        this.auctionEndingTransitionService = auctionEndingTransitionService == null
                ? Optional.empty() : auctionEndingTransitionService;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${auction.closing.scheduler.fixed-delay-ms:60000}",
            scheduler = "auctionBackupTaskScheduler"
    )
    public void closeDueAuctions() {
        Instant now = clock.instant();
        log.debug("event=auction.close.backup_scheduler.started now={} batchSize={}", now, CLOSE_BATCH_SIZE);
        try {
            var auctionIds = auctionCloseSchedulerProcessor.processDueAuctions(now, CLOSE_BATCH_SIZE);
            if (auctionIds.isEmpty()) {
                log.debug("event=auction.close.backup_scheduler.empty now={}", now);
                return;
            }
            log.info(
                    "event=auction.close.backup_scheduler.completed closedCount={} auctionIds={}",
                    auctionIds.size(), auctionIds
            );
        } catch (RuntimeException exception) {
            log.error("event=auction.close.backup_scheduler.failed now={} batchSize={}", now, CLOSE_BATCH_SIZE, exception);
            throw exception;
        }
    }

    @Scheduled(
            fixedDelayString = "${auction.closing.scheduler.fixed-delay-ms:60000}",
            scheduler = "auctionBackupTaskScheduler"
    )
    public void transitionOverdueEndingAuctions() {
        if (auctionEndingTransitionService.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        Instant threshold = now.plus(ENDING_WINDOW);
        List<Integer> auctionIds = auctionRepository.findOverdueEndingCandidateIds(
                threshold, PageRequest.of(0, CLOSE_BATCH_SIZE)
        );
        if (auctionIds.isEmpty()) {
            log.debug("event=auction.ending.backup_scheduler.empty now={}", now);
            return;
        }
        auctionIds.forEach(auctionId -> auctionEndingTransitionService.get().transitionIfDue(auctionId, now));
        log.info(
                "event=auction.ending.backup_scheduler.completed count={} auctionIds={}",
                auctionIds.size(), auctionIds
        );
    }
}
```

`redis` 프로필에서는 `AuctionEndingTransitionService` 빈이 없으므로(Task 8에서 `@Profile("!redis")`) `Optional.empty()`가 주입되고, `transitionOverdueEndingAuctions()`는 즉시 리턴한다 — `closeDueAuctions()`는 이번 변경과 무관하게 그대로 동작한다.

- [ ] **Step 4: 테스트 재실행**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.service.AuctionClosingSchedulerTest'
```

Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/dbidding/auction/service/AuctionClosingScheduler.java \
  backend/src/test/java/com/dbidding/auction/service/AuctionClosingSchedulerTest.java
git commit -m "feat: 60초 백업 폴러에 ENDING 전환 안전망 추가"
```

---

### Task 11: 백엔드 전체 회귀

**Files:**
- Verify: `backend/src/main/java/com/dbidding/auction/**`
- Verify: `backend/src/test/java/com/dbidding/auction/**`

- [ ] **Step 1: auction 패키지 전체 테스트**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.*'
```

Expected: PASS. Task 1~10에서 다룬 파일 밖에서 `placeBid`(구 시그니처)나 `extendCloseTimeIfNeeded`를 참조하던 곳이 남아있다면 여기서 컴파일 에러로 드러난다 — `grep -rn "extendCloseTimeIfNeeded\|placeBid(.*Duration" backend/src`로 잔재를 찾아 정리한다.

- [ ] **Step 2: 전체 백엔드 테스트**

```bash
cd backend
./gradlew clean test
```

Expected: 이번 변경 범위(auction 패키지) 테스트는 모두 통과. 무관한 기존 실패(있다면)는 이 변경이 만든 게 아님을 실패 로그로 확인만 하고 넘어간다.

- [ ] **Step 3: 커밋(수정 사항이 있었다면)**

```bash
git add -A
git commit -m "test: 마감임박 전환 변경에 따른 잔여 회귀 정리"
```

변경 사항이 없으면 이 Step은 건너뛴다.

---

### Task 12: 프론트엔드 — ENDING 상태 정적 표시

**Files:**
- Modify: `frontend/src/hooks/useCountdown.ts`
- Modify: `frontend/src/hooks/useCountdown.test.ts`
- Modify: `frontend/src/pages/auction/components/AuctionCatalog.tsx`
- Modify: `frontend/src/pages/auction-detail/AuctionDetailPage.tsx`

**Interfaces:**
- Produces: `useCountdown.ts`에 `export function displayRemaining(status: string, endsAt: string, now: number): string` 신규 — `ENDING`이면 `formatRemaining` 결과와 무관하게 `'마감임박'`을 돌려주고, 그 외엔 기존 `formatRemaining` 그대로.
- `isAuctionEnded`는 그대로 둔다(종료 판정 자체는 안 바뀐다 — ENDING은 여전히 "종료 아님").

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`useCountdown.test.ts`에 추가한다(기존 파일의 import·스타일을 따른다).

```ts
import {describe,expect,it} from 'vitest';
import {displayRemaining} from './useCountdown';

describe('displayRemaining',()=>{
  it('OPEN이면 기존 HH:MM:SS 카운트다운을 그대로 보여준다',()=>{
    const endsAt=new Date(Date.now()+3661_000).toISOString();
    expect(displayRemaining('OPEN',endsAt,Date.now())).toMatch(/^\d{2}:\d{2}:\d{2}$/);
  });

  it('ENDING이면 남은시간과 무관하게 마감임박을 보여준다',()=>{
    const endsAt=new Date(Date.now()+3661_000).toISOString();
    expect(displayRemaining('ENDING',endsAt,Date.now())).toBe('마감임박');
  });

  it('ENDING이어도 실제로 시간이 다_지났으면_경매_종료를_보여준다',()=>{
    const endsAt=new Date(Date.now()-1_000).toISOString();
    expect(displayRemaining('ENDING',endsAt,Date.now())).toBe('경매 종료');
  });
});
```

- [ ] **Step 2: 실패 확인**

```bash
cd frontend
npm test -- --run src/hooks/useCountdown.test.ts
```

Expected: `displayRemaining`이 없어 실패.

- [ ] **Step 3: `useCountdown.ts`에 함수를 추가한다**

파일 끝에 추가한다.

```ts
export function displayRemaining(status:string,endsAt:string,now:number):string{
  const remaining=formatRemaining(endsAt,now);
  if(remaining==='경매 종료')return remaining;
  return status==='ENDING'?'마감임박':remaining;
}
```

- [ ] **Step 4: 테스트 재실행**

```bash
cd frontend
npm test -- --run src/hooks/useCountdown.test.ts
```

Expected: PASS.

- [ ] **Step 5: `AuctionCatalog.tsx`와 `AuctionDetailPage.tsx`에서 표시 함수를 교체한다**

`AuctionCatalog.tsx`의 `import {formatRemaining,isAuctionEnded,useCountdownNow}...`를 `import {displayRemaining,formatRemaining,isAuctionEnded,useCountdownNow}...`로 바꾸고, `const remaining=formatRemaining(auction.endsAt,now)`는 `ended`/`isAuctionEnded` 판정에만 계속 쓰고, **화면에 렌더링하는 값**은 `displayRemaining(auction.status,auction.endsAt,now)`로 바꾼다(렌더링 지점은 `formatRemaining(...)`을 JSX에 직접 꽂아 쓰는 곳을 찾아 교체 — `grep -n "remaining" frontend/src/pages/auction/components/AuctionCatalog.tsx`로 정확한 줄을 찾는다).

`AuctionDetailPage.tsx`도 동일하게 `const remaining=formatRemaining(detail.ends_at,now)`는 `ended` 판정용으로 유지하고, 실제 카운트다운을 렌더링하는 JSX 지점에서 `displayRemaining(detail.status,detail.ends_at,now)`를 쓰도록 바꾼다.

두 파일 다 입찰 버튼 활성화 여부는 `ended`(→ `isAuctionEnded` 결과)만 보고 있는지 확인한다 — `status==='ENDING'`을 종료로 오인해 버튼을 막는 조건이 있으면 그대로 두되(이미 `isAuctionEnded`가 `ENDING`을 종료로 안 치므로 문제없어야 한다), 있다면 이 Step에서 제거한다.

- [ ] **Step 6: 프론트 관련 컴포넌트 테스트 실행**

```bash
cd frontend
npm test -- --run src/pages/auction/components/AuctionCatalog.test.tsx \
  src/pages/auction-detail/AuctionDetailPage.test.tsx 2>&1 | tail -60
```

Expected: PASS. 기존 테스트가 `formatRemaining`이 반환하던 HH:MM:SS 문자열을 ENDING 픽스처에서 그대로 기대하고 있었다면, 그 부분만 `'마감임박'` 기대값으로 고친다.

- [ ] **Step 7: 커밋**

```bash
git add frontend/src/hooks/useCountdown.ts frontend/src/hooks/useCountdown.test.ts \
  frontend/src/pages/auction/components/AuctionCatalog.tsx \
  frontend/src/pages/auction-detail/AuctionDetailPage.tsx
git commit -m "feat: ENDING 상태 카운트다운을 정적 마감임박 표시로 전환"
```

---

### Task 13: 프론트엔드 — `AUCTION_ENDING_STARTED` SSE 수신

**Files:**
- Modify: `frontend/src/hooks/useAuctionStream.ts`
- Modify: `frontend/src/queries/auctionStreamCache.ts`
- Modify: `frontend/src/queries/auctionStreamCache.test.ts`

**Interfaces:**
- Consumes: 백엔드 Task 6의 `AUCTION_ENDING_STARTED` 이벤트(필드: `AuctionStreamBase`와 동일 — `auction_id`, `start_price`, `current_price`, `bid_increment`, `bid_count`, `ends_at`, `status`, `event_id`, `occurred_at`)
- Produces: `AuctionStreamPayload` TS 유니온에 `'AUCTION_ENDING_STARTED'` variant 추가. `applyAuctionEvent`는 기존 map-merge 로직을 그대로 타므로(이미 `AUCTION_CLOSED` 외 모든 타입을 동일하게 처리) 별도 분기 없이 통과해야 한다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`auctionStreamCache.test.ts`에 추가한다(기존 `BID_PLACED` 케이스 테스트의 픽스처 스타일을 그대로 따른다).

```ts
it('AUCTION_ENDING_STARTED 이벤트를 받으면 status와 endsAt이 갱신된다',()=>{
  const auctions=[baseAuction({id:1,status:'OPEN',endsAt:'2026-08-12T10:05:00.000Z'})];
  const event={
    type:'AUCTION_ENDING_STARTED',
    auction_id:1,
    start_price:10_000,
    current_price:10_000,
    bid_increment:1_000,
    bid_count:0,
    ends_at:'2026-08-12T10:00:00.000Z',
    status:'ENDING',
    event_id:5,
    occurred_at:'2026-08-12T10:00:00.000Z',
  } as AuctionStreamPayload;

  const updated=applyAuctionEvent(auctions,event);

  expect(updated[0].status).toBe('ENDING');
  expect(updated[0].endsAt).toBe('2026-08-12T10:00:00.000Z');
});
```

`baseAuction(...)` 헬퍼가 이 테스트 파일에 이미 있으면 그대로 쓰고, 없으면 이 파일의 다른 테스트가 쓰는 방식(리터럴 객체 스프레드 등)을 그대로 따라 만든다.

- [ ] **Step 2: 실패 확인**

```bash
cd frontend
npm test -- --run src/queries/auctionStreamCache.test.ts 2>&1 | tail -30
```

Expected: TS 타입 에러(`'AUCTION_ENDING_STARTED'`가 `AuctionStreamPayload` 유니온에 없음) 또는 테스트 실패.

- [ ] **Step 3: `useAuctionStream.ts`를 수정한다**

`AUCTION_STREAM_EVENT_TYPES` 배열에 추가한다.

```ts
const AUCTION_STREAM_EVENT_TYPES=['AUCTION_CREATED','BID_PLACED','AUCTION_CLOSED','AUCTION_ENDING_STARTED'] as const;
```

`AuctionStreamPayload` 유니온에 variant를 추가한다(`BID_PLACED`처럼 카드 스냅샷 없이 최소 필드만).

```ts
export type AuctionStreamPayload=
  |AuctionStreamBase&AuctionCardSnapshot&{
    type:'AUCTION_CREATED';
    seller_id:number;
  }
  |AuctionStreamBase&{
    type:'BID_PLACED';
    bidder_id:number;
    previous_bidder_id:number|null;
  }
  |AuctionStreamBase&AuctionCardSnapshot&{
    type:'AUCTION_CLOSED';
    seller_id:number;
    winner_id:number|null;
  }
  |AuctionStreamBase&{
    type:'AUCTION_ENDING_STARTED';
  };
```

`parsePayload`의 타입별 추가 검증 블록(`if(value.type!=='BID_PLACED'&&(...))`, `if(value.type==='BID_PLACED'&&...)`)은 `AUCTION_ENDING_STARTED`엔 카드/판매자 필드도 `bidder_id`도 없으므로, 첫 번째 조건(`카드 스냅샷 필수 검증`)에 `AUCTION_ENDING_STARTED`도 `BID_PLACED`처럼 예외 처리해야 한다.

```ts
if(value.type!=='BID_PLACED'&&value.type!=='AUCTION_ENDING_STARTED'&&(
  !Number.isInteger(value.card_id)
  ||typeof value.card_name!=='string'
  ||!Number.isInteger(value.seller_id)
))return null;
```

- [ ] **Step 4: 테스트 재실행**

```bash
cd frontend
npm test -- --run src/queries/auctionStreamCache.test.ts
```

Expected: PASS. `applyAuctionEvent`/`auctionStreamCache.ts` 자체는 이미 타입 무관하게 동작하므로 수정이 필요 없어야 한다 — 만약 타입 좁히기(`event.type==='BID_PLACED'` 등 명시적 분기) 때문에 컴파일 에러가 나면, 해당 분기에 `'AUCTION_ENDING_STARTED'`를 안전하게 무시하도록(기존 map-merge 결과만 쓰도록) 최소한으로 고친다.

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/hooks/useAuctionStream.ts \
  frontend/src/queries/auctionStreamCache.ts \
  frontend/src/queries/auctionStreamCache.test.ts
git commit -m "feat: 프론트에서 AUCTION_ENDING_STARTED SSE 이벤트 수신"
```

---

### Task 14: 프론트엔드 전체 회귀 및 타입체크

**Files:**
- Verify: `frontend/src/**`

- [ ] **Step 1: 타입체크**

```bash
cd frontend
npm run typecheck 2>&1 | tail -40
```

Expected: 에러 없음. (스크립트 이름이 다르면 `package.json`의 `scripts`에서 확인 후 그 명령으로 대체한다.)

- [ ] **Step 2: 이번 변경이 건드린 테스트 전체 실행**

```bash
cd frontend
npm test -- --run src/hooks/useCountdown.test.ts \
  src/pages/auction/components/AuctionCatalog.test.tsx \
  src/pages/auction-detail/AuctionDetailPage.test.tsx \
  src/queries/auctionStreamCache.test.ts
```

Expected: PASS.

- [ ] **Step 3: 필요 시 정리 커밋**

```bash
git add -A
git commit -m "test: 마감임박 프론트 변경 잔여 회귀 정리"
```

변경 사항이 없으면 건너뛴다.

## 완료 조건

- OPEN 경매가 입찰 없이도 남은시간 5분 이하가 되면 자동으로 `ENDING` 전환된다(정밀 스케줄러 또는 60초 백업 폴러).
- `ENDING` 전환 시 정확히 1회만 1~2분(60~120초) 사이 균등분포 랜덤 값이 `closeTime`에 더해지고, 이후 같은 경매에 입찰이 더 들어와도 추가 연장이 없다.
- 목록/상세/대시보드 API와 SSE의 `ends_at`/`endsAt` 어디에도 ENDING 이후의 진짜 `closeTime`(랜덤 연장 반영값)이 노출되지 않는다. `closedAt`류 종료 확정 시각은 실제 값을 유지한다.
- 정밀 스케줄러가 놓친 경우를 대비한 60초 백업 폴러 안전망이 ENDING 전환에도 동작한다.
- `dbidding.auction.ending.transitions` 카운터가 ENDING 전환마다 1씩 증가한다.
- 프론트 목록/상세에서 `ENDING` 상태는 실시간 카운트다운 대신 정적 "마감임박"을 보여주고, `AUCTION_ENDING_STARTED` SSE 수신 시 즉시 반영된다.
- Redis 경로(`RedisBidExecutor`, `bid-accept.lua`, `applyStreamBid`, `RedisAuctionCloseSchedulerProcessor`)는 이번 변경으로 손대지 않는다.
- 백엔드(`./gradlew clean test`)와 프론트(`npm run typecheck`, 관련 컴포넌트 테스트)가 모두 통과한다.

> 이 문서는 Claude의 도움을 받아 작성하였습니다
