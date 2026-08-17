# AuctionBidStreamConsumer 순차 배치 처리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `AuctionBidStreamConsumer`가 PENDING 이벤트를 처리할 때 매번
1건씩 SELECT하던 걸 N건 배치 SELECT로 바꿔, 단일 스레드 처리량 상한을
DB 라운드트립 비용만큼 끌어올린다.

**Architecture:** 처리 순서·트랜잭션 경계·에러 처리 시맨틱은 전혀 바꾸지
않는다. `AuctionTimelineEventRepository`에 이미 있는
`findByProjectionStatusInOrderByIdAsc(Collection, Pageable)`를 재사용해
PENDING N건을 한 번에 가져오고, 가져온 건들을 지금과 똑같이 id 오름차순
한 스레드에서 순차 처리한다. 배치 안에서도 이벤트 하나가 에러로 markError되면
그 즉시 배치 처리를 중단해, "에러 발생 시 즉시 멈추고 `requeueFirstError()`
경로로 넘어간다"는 기존 시맨틱을 그대로 보존한다.

**Tech Stack:** Java 21, Spring Boot 4.1, JPA, MySQL 8.4(Testcontainers),
JUnit 5, Mockito, AssertJ

## Global Constraints

- 이 작업의 범위는 **SELECT 배치화**로 한정한다. 이벤트 처리 순서(id
  오름차순), 이벤트 1건당 트랜잭션 경계(`project()`/`markProcessed()`/
  `markError()`가 각각 `REQUIRES_NEW` 또는 자체 `@Transactional`인 것),
  에러 발생 시 즉시 멈추는 시맨틱은 바꾸지 않는다.
- **경매ID 파티셔닝 등 병렬 처리는 이 작업 범위 밖이다.** 별도로 검토가
  필요하면 새 이슈로 분리한다 — #565 이슈 본문에는 "지갑 락 순서를
  전역 기준으로 재설계해야 한다"고 적어뒀지만, 이 문서를 작성하며 코드를
  다시 읽어보니 `AuctionBidStreamPersistenceService.applyWalletTransition()`의
  `previousBidderId < event.bidderId()` 비교가 **경매와 무관하게 이미
  전역 사용자ID 기준**이라 표준 락 순서 정리(lock-ordering theorem)상
  교차 데드락이 이미 안 생기는 구조로 보인다(`docs/hyeonmoon/auction/1-bid-wallet-lock-ordering-deadlock.md`가
  `AuctionCommandService.participate()` 경로에 적용한 것과 사실상 동일한
  규칙). 이 발견은 **미검증 재확인**이며, 병렬화를 실제로 추진할 때
  전용 동시성 테스트로 다시 검증해야 한다 — 이 문서의 작업 범위에는
  포함하지 않는다.
- `RedisProjectionCatchUpVerifier`의 전역 게이트 좁히기(경매별 게이트)도
  범위 밖이다 — #565 이슈에 이미 별도 후속 조치로 적혀 있다.
- Redis Stream 자체의 읽기/ACK/claim 로직(`consumeUntilIdle()`의 Stream
  읽기 절반)은 바꾸지 않는다. PENDING 배치 처리(`projectOldestPending()`
  쪽)만 바꾼다.

---

## 1. 문제 배경

10차 부하테스트(`docs/hyeonmoon/observability/14-round10-merged-wallet-notification-sse-validation.md`
§4)에서 확인된 내용:

- `AuctionBidStreamConsumer.java:56-57`가 `Executors.newSingleThreadExecutor`로
  고정된 단일 스레드다.
- `consumeUntilIdle()`(`AuctionBidStreamConsumer.java:98-113`)의
  `projectOldestPending()`(`AuctionBidStreamConsumer.java:145-164`)이
  PENDING 이벤트를 **1건씩** `findFirstByProjectionStatusOrderByIdAsc()`로
  SELECT한다 — 이벤트 하나 처리할 때마다 SELECT 왕복이 하나씩 그대로
  누적된다.
- 세션(78분) 내내 백로그를 못 따라잡아 `GET .../bid-context`가 세션 구간
  총 144,073회 503을 반환했다(같은 API 200 성공 223,563건 대비 39.2%,
  9차 11,718건의 약 12배). 세션 종료 5분 반 뒤 재확인 시 PENDING 1건까지
  자연 드레인됨 — poison-pill이 아니라 순수 "유입 > 처리 속도"인 처리량
  문제였다.
- `findFirstByProjectionStatusOrderByIdAsc`가 쓰는
  `idx_timeline_events_projection_status` 단일 컬럼 인덱스는 InnoDB
  특성상 PK(id) 순으로 이미 정렬돼 있어 `ORDER BY id LIMIT 1`에
  filesort가 필요 없다(`EXPLAIN ANALYZE`로 직접 확인) — 즉 병목은 인덱스
  설계가 아니라 **이벤트 수만큼 반복되는 SELECT 왕복 횟수** 자체다.

## 2. 해결 방향

`AuctionTimelineEventRepository`에는 이미 배치 조회용 메서드가 있다.

```java
Page<AuctionTimelineEvent> findByProjectionStatusInOrderByIdAsc(
        Collection<AuctionBidEventProjectionStatus> statuses, Pageable pageable);
```

새 리포지토리 메서드를 추가할 필요 없이, PENDING 1건 조회 대신 이 메서드를
`List.of(PENDING)` + `PageRequest.of(0, batchSize)`로 호출해 N건을 한 번에
가져온다. 가져온 리스트는 이미 id 오름차순이므로, 지금과 동일하게 한
스레드에서 순서대로 `project()`→`markProcessed()`를 호출한다. 배치 도중
`persistenceService.hasProjectionError()`가 true가 되면(같은 배치 안의
이전 이벤트가 방금 에러났거나, 다른 경로에서 이미 에러가 있었으면) 그
즉시 남은 배치를 버리고 리턴한다 — 지금 `projectOldestPending()` 맨 위의
`hasProjectionError()` 체크와 동일한 효과를 배치 안에서도 유지한다.

## 3. 범위 확인

`consumeUntilIdle()`의 두 갈래(PENDING 프로젝션 / Redis Stream 수신) 중
**PENDING 프로젝션 쪽만** 바꾼다. Redis Stream 읽기·claim·ACK 경로는
그대로 둔다.

| 대상 | 현재 | 변경 후 |
|---|---|---|
| PENDING 조회 | `findFirstByProjectionStatusOrderByIdAsc` (1건) | `findByProjectionStatusInOrderByIdAsc` (N건, 기존 메서드 재사용) |
| 처리 순서 | id 오름차순 | 동일 |
| 트랜잭션 경계 | 이벤트당 1개 | 동일 (배치 안에서도 이벤트별로 유지) |
| 에러 시 동작 | 즉시 중단, `requeueFirstError()`로 복구 | 동일 |
| Stream 읽기 경로 | 별도 | 변경 없음 |

---

### Task 1: 배치 크기 설정값 추가

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/stream/AuctionBidStreamProperties.java`
- Modify: `backend/src/test/java/com/dbidding/auction/stream/AuctionBidStreamPropertiesTest.java` (없으면 생성)

**Interfaces:**
- Produces: `AuctionBidStreamProperties.pendingBatchSize()` — `int`, 기본값 20

- [ ] **Step 1: 실패하는 테스트를 작성한다**

```java
@Test
void pendingBatchSize_기본값은_20이다() {
    AuctionBidStreamProperties properties = new AuctionBidStreamProperties(
            null, null, 0, null, 0, 0);
    assertThat(properties.pendingBatchSize()).isEqualTo(20);
}

@Test
void pendingBatchSize가_1_미만이면_예외() {
    assertThatThrownBy(() -> new AuctionBidStreamProperties(null, null, 0, null, 0, 0)
            .withPendingBatchSize(0))
            .isInstanceOf(IllegalArgumentException.class);
}
```

(정확한 생성자 시그니처는 기존 record 필드 순서에 새 필드를 추가한 형태로
맞춘다 — 아래 Step 2 참고.)

- [ ] **Step 2: 테스트가 컴파일 실패하는지 확인한다**

```bash
cd backend
./gradlew compileTestJava
```

Expected: `pendingBatchSize` 필드/메서드가 없어 컴파일 에러.

- [ ] **Step 3: record에 필드를 추가한다**

```java
@ConfigurationProperties("app.auction.redis-bid")
public record AuctionBidStreamProperties(
        Duration block,
        Duration claimIdle,
        int maxRetries,
        Duration consumerLockAtMostFor,
        int maxRecordsPerRun,
        int pendingBatchSize
) {
    public AuctionBidStreamProperties {
        block = block == null ? Duration.ofSeconds(1) : block;
        claimIdle = claimIdle == null ? Duration.ofSeconds(30) : claimIdle;
        maxRetries = maxRetries == 0 ? 3 : maxRetries;
        consumerLockAtMostFor = consumerLockAtMostFor == null ? Duration.ofMinutes(5) : consumerLockAtMostFor;
        maxRecordsPerRun = maxRecordsPerRun == 0 ? 100 : maxRecordsPerRun;
        pendingBatchSize = pendingBatchSize == 0 ? 20 : pendingBatchSize;
        if (consumerLockAtMostFor.isNegative() || consumerLockAtMostFor.isZero()) {
            throw new IllegalArgumentException("consumerLockAtMostFor는 양수여야 합니다.");
        }
        if (maxRecordsPerRun < 1) {
            throw new IllegalArgumentException("maxRecordsPerRun은 1 이상이어야 합니다.");
        }
        if (pendingBatchSize < 1) {
            throw new IllegalArgumentException("pendingBatchSize는 1 이상이어야 합니다.");
        }
    }
}
```

기존 생성자 호출부(운영 설정 바인딩, 기존 테스트의 `new
AuctionBidStreamProperties(...)` 호출)는 필드가 하나 늘어난 만큼 인자를
추가해야 한다 — 컴파일 에러가 나는 지점을 전부 찾아 `0`(기본값 20 적용)
또는 명시값으로 채운다.

- [ ] **Step 4: 테스트 통과 확인 후 커밋**

```bash
./gradlew test --tests com.dbidding.auction.stream.AuctionBidStreamPropertiesTest
git add backend/src/main/java/com/dbidding/auction/stream/AuctionBidStreamProperties.java \
  backend/src/test/java/com/dbidding/auction/stream/AuctionBidStreamPropertiesTest.java
git commit -m "feat: AuctionBidStreamConsumer PENDING 배치 크기 설정값 추가"
```

### Task 2: `projectOldestPending()`를 배치 처리로 교체

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/stream/AuctionBidStreamConsumer.java`
- Modify: `backend/src/test/java/com/dbidding/auction/stream/AuctionBidStreamConsumerTest.java`

**Interfaces:**
- Consumes: `AuctionTimelineEventRepository.findByProjectionStatusInOrderByIdAsc(Collection, Pageable)`
  (기존 메서드, 시그니처 변경 없음), `AuctionBidStreamProperties.pendingBatchSize()`(Task 1)
- Produces: `projectPendingBatch()` — 기존 `projectOldestPending()`을
  대체. 반환 타입·의미(처리한 게 있으면 true, 없으면 false)는 동일하게
  유지해 `consumeUntilIdle()`의 `if (projectOldestPending()) continue;`
  호출부를 이름만 바꿔 그대로 쓸 수 있게 한다.

- [ ] **Step 1: 배치 처리 동작을 검증하는 실패 테스트를 작성한다**

```java
@Test
void PENDING_이벤트를_한번에_N건_조회해_id_순서대로_처리한다() {
    // given: PENDING 이벤트 3건을 id 오름차순으로 미리 저장
    // AuctionBidStreamProperties.pendingBatchSize() = 20인 기본 설정에서
    // repository.findByProjectionStatusInOrderByIdAsc가 정확히 1번만
    // 호출되는지 검증(현재 구현은 이벤트 수만큼 반복 호출됨)
    consumer.consumeOnceForTest(); // 또는 기존 테스트가 쓰는 진입점

    verify(inboxRepository, times(1))
            .findByProjectionStatusInOrderByIdAsc(eq(List.of(PENDING)), any(Pageable.class));
    // then: 3건이 id 오름차순으로 project()/markProcessed() 호출됨을 검증
}

@Test
void 배치_처리_도중_에러가_나면_남은_배치를_버리고_즉시_중단한다() {
    // given: PENDING 3건, 두 번째 이벤트에서 projectWithRetry가 실패하도록 스텁
    // then: 세 번째 이벤트는 project()가 호출되지 않았는지 검증(verify(never()))
}
```

- [ ] **Step 2: 테스트가 현재 구현에서 실패하는지 확인한다**

```bash
./gradlew test --tests com.dbidding.auction.stream.AuctionBidStreamConsumerTest
```

Expected: `findByProjectionStatusInOrderByIdAsc` 호출 검증 실패
(현재는 `findFirstByProjectionStatusOrderByIdAsc`를 이벤트마다 호출).

- [ ] **Step 3: `projectOldestPending()`을 배치 버전으로 교체한다**

```java
private boolean projectPendingBatch() {
    if (persistenceService.hasProjectionError()) return false;
    List<AuctionTimelineEvent> batch = inboxRepository.findByProjectionStatusInOrderByIdAsc(
            List.of(AuctionBidEventProjectionStatus.PENDING),
            PageRequest.of(0, properties.pendingBatchSize())
    ).getContent();
    if (batch.isEmpty()) return false;
    for (AuctionTimelineEvent inbox : batch) {
        if (persistenceService.hasProjectionError()) break;
        projectSingle(inbox);
    }
    return true;
}

private void projectSingle(AuctionTimelineEvent inbox) {
    try {
        AuctionWalletTimelineEvent event = AuctionWalletTimelineEvent.from(
                inbox.getStreamId(), objectMapper.readValue(inbox.getPayload(), new TypeReference<>() {}));
        RuntimeException failure = projectWithRetry(event);
        if (failure == null) persistenceService.markProcessed(event.streamId());
        else if (persistenceService.markError(event.streamId(), failure)) log.error(
                "event=auction.bid.inbox.projection.error streamId={} auctionId={}", event.streamId(), inbox.getAuctionId(), failure);
    } catch (Exception exception) {
        RuntimeException failure = exception instanceof RuntimeException runtime ? runtime : new IllegalStateException(exception);
        if (persistenceService.markError(inbox.getStreamId(), failure)) {
            log.error("event=auction.bid.inbox.payload.error streamId={}", inbox.getStreamId(), failure);
        }
    }
}
```

`projectOldestPending()`의 기존 본문(단건 처리 로직)을 그대로
`projectSingle(AuctionTimelineEvent)`로 옮기고, `consumeUntilIdle()`의
호출부(`AuctionBidStreamConsumer.java:100`)를 `projectOldestPending()` →
`projectPendingBatch()`로 바꾼다. import에
`org.springframework.data.domain.PageRequest`, `java.util.List` 추가.

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests com.dbidding.auction.stream.AuctionBidStreamConsumerTest
```

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/dbidding/auction/stream/AuctionBidStreamConsumer.java \
  backend/src/test/java/com/dbidding/auction/stream/AuctionBidStreamConsumerTest.java
git commit -m "perf: AuctionBidStreamConsumer PENDING 이벤트를 배치로 조회하도록 변경"
```

### Task 3: 전체 회귀 검증 + 부하테스트로 실측 재확인 (선택)

**Files:**
- Verify: `backend/src/main/java/com/dbidding/auction/stream/**`

- [ ] **Step 1: 전체 백엔드 테스트 실행**

```bash
cd backend
./gradlew clean test
```

Expected: 기존 비관련 실패를 제외하고 auction.stream 패키지 전체 통과.
테스트 소스가 없는 패턴은 통과로 표현하지 않는다.

- [ ] **Step 2 (선택): 부하테스트로 처리량 개선 실측**

`docs/hyeonmoon/observability/12-load-test-operations-guide.md`의 표준
6종 실행 세트를 다시 돌려, 세션 구간 `GET .../bid-context` 503 비율이
10차(39.2%) 대비 낮아졌는지, `timeline_events` 5분+ 지연 비율(10차
89.9%)이 줄었는지 Prometheus/DB로 직접 확인한다. 배치 크기(`pendingBatchSize`)를
바꿔가며(예: 20/50/100) 처리량 개선 폭을 비교하는 것도 고려한다.

## 완료 조건

- `AuctionBidStreamConsumer`가 PENDING 이벤트를 조회할 때 이벤트 수만큼이
  아니라 배치 1번(설정된 배치 크기만큼)에 걸쳐 SELECT한다.
- 이벤트 처리 순서(id 오름차순)와 이벤트별 트랜잭션 경계는 배치 적용
  전후로 동일하다.
- 배치 도중 에러가 나면 그 즉시 중단하고 기존 `requeueFirstError()`
  복구 경로로 넘어가는 동작이 유지된다.
- `RedisProjectionCatchUpVerifier`의 전역 게이트 좁히기, 경매ID
  파티셔닝 병렬 처리는 이 문서 범위 밖이며, 후자는 지갑 락 순서가 이미
  전역 기준인지 여부를 먼저 전용 동시성 테스트로 재검증해야 한다(§Global
  Constraints).

## 참고

- 이슈: [#565](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/565)
- 10차 부하테스트: `docs/hyeonmoon/observability/14-round10-merged-wallet-notification-sse-validation.md` §4, §4.7, §4.8
- 지갑 락 순서 선례: `docs/hyeonmoon/auction/1-bid-wallet-lock-ordering-deadlock.md`

> 이 문서는 claude의 도움을 받아 작성하였습니다
