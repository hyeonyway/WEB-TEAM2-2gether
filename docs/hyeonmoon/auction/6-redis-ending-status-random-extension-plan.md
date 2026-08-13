# Redis 마감임박(ENDING) 시간기준 전환 + 단발 랜덤연장 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `redis` 프로필 경매도 실제 마감 5분 전에 한 번만 `OPEN → ENDING`으로 전이하고, 실제 마감은 60~120초 단발 랜덤 연장한다. Redis hash·ZSET·Stream·MySQL projection·REST/SSE가 DB 프로필과 같은 공개 계약을 지키며, 기존 Redis 상태에서도 안전하게 배포한다.

**Architecture:** 공개용 예정 마감시각(`estimatedCloseTime`)과 ENDING 진입 예약용 ZSET을 분리한다. `auction-ending-transition.lua`가 상태 전이, 두 ZSET 갱신, timeline Stream 기록을 한 `EVAL`로 처리한다. 프로필별 `AuctionEndingTransitionProcessor`를 두 스케줄러가 공통으로 호출하고, Redis 구현체는 전이 성공 후 `AuctionCloseScheduleChangedEvent`를 발행해 실제 연장 마감으로 즉시 재예약한다.

**Tech Stack:** Java 21, Spring Boot 4.1, Redis Lua, Spring Data Redis, MySQL/JPA, JUnit 5, Mockito, AssertJ, Testcontainers Redis

**Related design:** [5-redis-ending-status-random-extension-design.md](5-redis-ending-status-random-extension-design.md)
**Related issue:** #436

## 설계 검토 반영 사항

- Lua에 ISO-8601 변환을 맡기지 않는다. Java가 `newCloseTime`의 ISO 문자열과 epoch milliseconds를 함께 전달한다.
- `now >= closeTime`인 OPEN 경매는 `NOOP|EXPIRED`로 끝내고 ending-window member만 제거한다. 늦은 실행이 종료 대상 경매를 ENDING으로 되살릴 수 없다.
- Redis 전이 성공 직후에도 `AuctionCloseScheduleChangedEvent`를 발행한다. 그렇지 않으면 deadline scheduler가 이전 마감 예약을 계속 기다릴 수 있다.
- `auction:ending-window:by-close-time`은 생성·시드·buy-now·일반 마감·ENDING 전이·정리 작업에서 모두 관리한다.
- 이미 존재하는 Redis hash에는 새 필드가 없을 수 있다. reader fallback과 기동 시 bounded repair를 함께 둔다.
- 내부 Stream에는 실제 마감시각을 기록하되, REST/SSE `endsAt`에는 `estimatedCloseTime`만 사용한다.

## Global Constraints

- `auction:active:by-close-time` score는 실제 종료 처리용 실제 마감시각이다. `auction:ending-window:by-close-time` score만 `estimatedCloseTime - AuctionEndingPolicy.WINDOW`다.
- 일반 입찰은 연장하지 않는다. `bid-accept.lua`의 `closeTimeExtended` 응답 필드는 Java 파서 호환을 위해 남기되 항상 `false`다.
- 랜덤값은 Java `EndingExtensionProvider`가 경매당 한 번만 뽑는다. Lua 난수나 Redis `TIME`은 사용하지 않는다.
- Stream projection은 랜덤값을 재계산하지 않고 전달받은 실제 `closeTime`만 멱등 반영한다.
- Redis Cluster hash tag, 프론트 변경, 정식 k6 검증은 이번 범위에서 제외한다.

---

### Task 1: Redis 상태와 인덱스 수명 확장

**Files:**

- Modify: `backend/src/main/resources/lua/auction-create.lua`
- Modify: `backend/src/main/resources/lua/auction-state-seed.lua`
- Modify: `backend/src/main/java/com/dbidding/auction/bid/RedisAuctionCreateExecutor.java`
- Modify: `backend/src/main/java/com/dbidding/auction/bid/RedisAuctionStateSeeder.java`
- Modify: `backend/src/main/resources/lua/bid-accept.lua`
- Modify: `backend/src/main/resources/lua/auction-close-request.lua`
- Modify: `backend/src/main/java/com/dbidding/auction/bid/RedisBidExecutor.java`
- Modify: `backend/src/main/java/com/dbidding/auction/service/RedisAuctionCloseSchedulerProcessor.java`
- Modify: `backend/src/main/java/com/dbidding/auction/service/RedisAuctionActiveIndexCleanupScheduler.java`
- Add: `backend/src/main/resources/lua/auction-ending-window-index-gc.lua`

**Interfaces:**

- New ZSET: `auction:ending-window:by-close-time`, member=`auctionId`, score=`estimatedCloseTimeEpochMillis - 300000`.
- New state fields: `estimatedCloseTime`, `estimatedCloseTimeEpochMillis`.

- [ ] Add failing create/seed Lua integration tests for both fields and the ending-window score. Extend buy-now and close-request tests to assert both indexes lose their member. Add a cleanup test proving a terminal/missing state is removed but a due OPEN state is retained for the transition processor.
- [ ] `RedisAuctionCreateExecutor` passes ending-window as the fourth Lua key. `auction-create.lua` writes estimated values equal to initial close values and adds the ending-window member.
- [ ] `RedisAuctionStateSeeder` passes the fourth key, writes values from `Auction.getEstimatedCloseTime()`, and `auction-state-seed.lua` adds ending membership only for `status == 'OPEN'`. ENDING states stay only in the real-close active index.
- [ ] `RedisBidExecutor` and `RedisAuctionCloseSchedulerProcessor` pass ending-window as an extra Lua key. The buy-now branch of `bid-accept.lua` and accepted path of `auction-close-request.lua` remove both indexes in their own `EVAL`.
- [ ] Add the ending-window GC script and call it from `RedisAuctionActiveIndexCleanupScheduler`; log active and ending removal counts separately. Do not age-remove a valid due OPEN member.
- [ ] Verify with `./gradlew test --tests 'com.dbidding.auction.bid.RedisAuctionCreateLuaIntegrationTest' --tests 'com.dbidding.auction.bid.RedisAuctionStateSeedLuaIntegrationTest' --tests 'com.dbidding.auction.service.RedisAuctionCloseRequestLuaIntegrationTest' --tests 'com.dbidding.auction.service.AuctionCloseRequestLuaIntegrationTest'`, then commit as `feat: Redis ENDING 전이용 상태와 인덱스 추가`.

---

### Task 2: Lua 전이와 Redis processor 구현

**Files:**

- Add: `backend/src/main/resources/lua/auction-ending-transition.lua`
- Modify: `backend/src/main/java/com/dbidding/auction/bid/RedisBidLuaConfiguration.java`
- Add: `backend/src/main/java/com/dbidding/auction/service/AuctionEndingTransitionProcessor.java`
- Add: `backend/src/main/java/com/dbidding/auction/service/RedisAuctionEndingTransitionProcessor.java`
- Modify: `backend/src/main/java/com/dbidding/auction/service/EndingExtensionProvider.java`
- Add: `backend/src/test/java/com/dbidding/auction/service/AuctionEndingTransitionLuaIntegrationTest.java`
- Add: `backend/src/test/java/com/dbidding/auction/service/RedisAuctionEndingTransitionProcessorTest.java`

**Interface:**

```java
public interface AuctionEndingTransitionProcessor {
    List<Integer> transitionDueAuctions(Instant now, int limit);
}
```

- [ ] Write Lua integration tests: due OPEN becomes ENDING with unchanged estimated time, one Stream event, a new active score, and no ending member; ENDING/ENDED reruns are no-op; early OPEN returns `NOOP|TOO_EARLY`; overdue OPEN returns `NOOP|EXPIRED`, changes no hash fields, and removes only ending membership.
- [ ] Bind `auction-ending-transition.lua` through a qualified `RedisScript<String>` bean. Its exact contract is `KEYS=[state, ending-window, active-by-close-time, event:timeline]`; `ARGV=[auctionId, nowEpochMillis, nowIso, newCloseTimeIso, newCloseTimeEpochMillis]`.
- [ ] Lua requires `status == OPEN`, required close fields, `now < closeTime`, and `closeTime - WINDOW <= now` before HSET/ZREM/ZADD/XADD. It never changes estimated fields. `auction.ending-started.v1` includes auction ID, actual close time/epoch, and occurrence time.
- [ ] `RedisAuctionEndingTransitionProcessor` reads `ZRANGEBYSCORE` candidates, calls `EndingExtensionProvider.next()` once per attempted candidate, calculates actual time in Java, and only on `TRANSITIONED` records metric/log/ID and publishes `AuctionCloseScheduleChangedEvent(auctionId, newCloseTime, "ending_transition")`. One Lua failure logs warn and does not stop later IDs; `NOOP` emits neither error nor event.
- [ ] Verify with `./gradlew test --tests 'com.dbidding.auction.service.AuctionEndingTransitionLuaIntegrationTest' --tests 'com.dbidding.auction.service.RedisAuctionEndingTransitionProcessorTest'`, then commit as `feat: Redis 경매 ENDING 단발 전이 처리 추가`.

---

### Task 3: 프로필 공통 스케줄러 계약으로 전환

**Files:**

- Modify: `backend/src/main/java/com/dbidding/auction/service/AuctionEndingTransitionService.java`
- Modify: `backend/src/main/java/com/dbidding/auction/service/AuctionDeadlineScheduler.java`
- Modify: `backend/src/main/java/com/dbidding/auction/service/AuctionClosingScheduler.java`
- Modify tests under `backend/src/test/java/com/dbidding/auction/service/` and `.../config/`

- [ ] `AuctionEndingTransitionService` implements `AuctionEndingTransitionProcessor` while keeping `@Profile("!redis")`. Its batch method finds OPEN candidates and delegates to the existing locked `transitionIfDue` method.
- [ ] Replace `Optional<AuctionEndingTransitionService>` with required `AuctionEndingTransitionProcessor` in both schedulers. Delete `AuctionClosingScheduler`'s direct repository query path. Both schedulers call ending processor first, then close processor. At the exact close instant this yields `NOOP|EXPIRED` then ordinary close.
- [ ] Redis `AuctionDeadlineScheduler.nextTarget()` reads the first score from active and ending-window ZSETs and selects the earlier target. The schedule-change event from Task 2 must cancel the old future and reschedule the extended actual close time.
- [ ] Add regression tests for exactly one processor per profile, target selection, ending-before-close order, and schedule change rescheduling. Verify with the existing `AuctionDeadlineSchedulerTest`, `AuctionDeadlineSchedulerTransactionTest`, `AuctionClosingSchedulerTest`, and profile configuration suites, then commit as `refactor: 마감임박 전이 스케줄러를 프로필 공통화`.

---

### Task 4: Stream projection과 공개 시각 마스킹

**Files:**

- Modify: `backend/src/main/java/com/dbidding/auction/domain/Auction.java`
- Add: `backend/src/main/java/com/dbidding/auction/stream/AuctionEndingStartedStreamEvent.java`
- Modify: `backend/src/main/java/com/dbidding/auction/stream/AuctionWalletTimelineEvent.java`
- Modify: `backend/src/main/java/com/dbidding/auction/stream/AuctionBidStreamPersistenceService.java`
- Modify: `backend/src/main/java/com/dbidding/auction/query/RedisAuctionRealtimeStateReader.java`
- Modify: `backend/src/main/java/com/dbidding/auction/service/AuctionQueryService.java`
- Modify associated tests

- [ ] Add `Auction.applyEndingTransition(Instant actualCloseTime)`: only OPEN applies, a non-forward time is rejected, actual close/status change, estimated close remains unchanged. Write parsing/projection tests proving `auction.ending-started.v1` changes the locked MySQL row once only and publishes `AuctionStreamPayload.endingStarted` only on first application.
- [ ] Add `AuctionEndingStartedStreamEvent` to `AuctionWalletTimelineEvent` permits and `from(...)`. In persistence, lock via `findByIdForUpdate`, apply the actual Stream time, and avoid duplicate SSE publish.
- [ ] Add `estimatedCloseTime` to Redis `AuctionState`. An old hash falls back to `closeTime` if the new field is absent rather than failing the Redis read. `AuctionQueryService` uses estimated time for active Redis summary/detail responses, actual time for terminal results; internal close checks keep actual time.
- [ ] Verify with `AuctionBidStreamPersistenceServiceTest`, `RedisAuctionRealtimeStateReaderTest`, `AuctionQueryServiceTest`, and `RedisDashboardServiceTest`, then commit as `feat: Redis ENDING Stream projection과 마감시각 마스킹 추가`.

---

### Task 5: 기존 Redis 상태 보정과 전체 회귀 검증

**Files:**

- Modify: `backend/src/main/java/com/dbidding/auction/bid/RedisAuctionStateWarmUp.java`
- Add/modify warm-up and end-to-end Redis transition tests
- Modify: `docs/hyeonmoon/auction/5-redis-ending-status-random-extension-design.md` only if implementation decisions change its intended contract

- [ ] Add a bounded startup scan of `auction:active:by-close-time`. Valid OPEN hashes missing estimated fields get both fields and an ending member; valid ENDING hashes get missing estimated fields but no ending member; missing/terminal hashes lose their ending member. Make batch size/property explicit and idempotent.
- [ ] Do not claim to reconstruct hidden extensions from old states: before this feature they had no comparable public-time contract. The repair prevents rollout failure and makes post-deploy transitions correct.
- [ ] Add Testcontainers Redis end-to-end regression: OPEN → test clock at ending score → transition → Stream projection. Assert Redis/MySQL actual close agreement, frozen REST/SSE time, later bid unchanged times, close only at extended score, and repeated transition/backup no duplicate extension or Stream event.
- [ ] Run `cd backend && ./gradlew clean test`. If Docker/Testcontainers is unavailable, report that prerequisite rather than calling the suite passing. Commit as `test: Redis 마감임박 전이 배포 보정과 회귀 검증 추가`.

## 완료 조건

- Redis OPEN 경매는 입찰 없이 실제 마감 5분 전에 정확히 한 번 ENDING으로 전이한다.
- 실제 마감은 60~120초만 한 번 연장되고, 재입찰은 시간과 상태를 바꾸지 않는다.
- 늦은 전이 호출은 이미 마감된 OPEN을 ENDING으로 바꾸지 않는다.
- create/seed/buy-now/close/transition/cleanup 어느 경로도 ending-window 고아 member를 남기지 않는다.
- 전이 성공 직후 deadline scheduler가 새 실제 마감시각으로 재예약한다.
- Redis Stream과 MySQL projection은 실제 마감시각에 대해 멱등 합의한다.
- Redis REST/SSE 활성 경매의 `endsAt`에는 `estimatedCloseTime`만 노출된다.
- 기존 Redis hash의 누락 필드는 reader fallback과 bounded repair로 무중단 처리된다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
