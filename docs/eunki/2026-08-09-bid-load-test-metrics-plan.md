# Bid Load-Test Metrics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose low-cardinality timers for the bid critical section, bid substeps, and JPA flush, and measure auction SSE delivery latency and send failures during k6 load tests.

**Architecture:** Extend `AuctionMetrics` with pre-registered timers and record the timers in `AuctionCommandService` after the auction lock is acquired. Add an auction SSE metrics component and a `publishedAt` field assigned by the broadcaster; k6 derives a client-side delivery trend from that timestamp. Existing Actuator-provided HTTP, Tomcat, Hikari, and executor metrics remain unchanged.

**Tech Stack:** Spring Boot, Micrometer, JUnit 5, Mockito, k6 with xk6-sse.

## Global Constraints

- Keep the default bid behavior and API response contracts unchanged except for the additive SSE `published_at` field.
- Use only fixed enum/string metric tags; never tag metrics with auction, user, bid, or request IDs.
- Do not measure transaction commit as part of the flush timer.
- Preserve the existing `dbidding.bid.duration`, lock-wait, and wallet metrics.
- Keep documentation under `docs/eunki`.

---

### Task 1: Bid timing metric definitions

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/metrics/AuctionMetrics.java`
- Test: `backend/src/test/java/com/dbidding/auction/metrics/AuctionMetricsTest.java`

**Interfaces:**
- Produces: `startBidCriticalSection()`, `finishBidCriticalSection(Timer.Sample)`, `recordBidStep(BidStep, Runnable)`, and `recordBidFlush(Timer.Sample)`.
- Produces: `BidStep.OUTBID`, `BidStep.HOLD`, and `BidStep.SAVE` with `step` tags.

- [x] **Step 1: Write failing tests** for `dbidding.bid.critical_section.duration`, each `dbidding.bid.step.duration{step=...}`, and `dbidding.bid.db_flush.duration`.
- [x] **Step 2: Run the metrics test** and verify it fails because the new metric APIs do not exist.
- [x] **Step 3: Add the smallest pre-registered timer maps and recording methods** with percentile histograms and existing operation SLO boundaries.
- [x] **Step 4: Run the metrics test** and verify it passes.

### Task 2: Instrument the accepted bid flow

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/service/AuctionCommandService.java`
- Test: `backend/src/test/java/com/dbidding/auction/service/AuctionServiceBidTest.java`

**Interfaces:**
- Consumes: `AuctionMetrics.startBidCriticalSection`, `recordBidStep`, and `recordBidFlush` from Task 1.
- Produces: accepted bids record one critical-section timer; `outbid`, `hold`, and `save` timers record only when their corresponding operation executes; the flush timer surrounds only `auctionRepository.flush()`.

- [x] **Step 1: Write failing service tests** that submit an accepted bid and assert each applicable timer count is one.
- [x] **Step 2: Run the focused service test** and verify it fails because no new timers are recorded.
- [x] **Step 3: Start the critical-section timer after `findByIdForUpdate` returns**, wrap hold/outbid/save calls using `recordBidStep`, and surround only `auctionRepository.flush()` with a flush sample.
- [x] **Step 4: Run the focused service test** and verify it passes.

### Task 3: SSE publish and send metrics

**Files:**
- Create: `backend/src/main/java/com/dbidding/auction/sse/AuctionSseMetrics.java`
- Modify: `backend/src/main/java/com/dbidding/auction/sse/AuctionSseConnectionManager.java`
- Modify: `backend/src/main/java/com/dbidding/auction/sse/AuctionStreamPayload.java`
- Modify: `backend/src/test/java/com/dbidding/auction/sse/AuctionSseContractTest.java`
- Create: `backend/src/test/java/com/dbidding/auction/sse/AuctionSseMetricsTest.java`

**Interfaces:**
- Produces: `dbidding.sse.delivery.latency` as a k6-side Trend using `published_at`.
- Produces: server-side `dbidding.auction.sse.send.duration` and `dbidding.auction.sse.send.failures`.
- Produces: `AuctionStreamPayload.withPublishedAt(Instant)` to add the timestamp without changing domain event construction.

- [x] **Step 1: Write failing tests** for SSE send success/failure meters and `published_at` serialization.
- [x] **Step 2: Run the focused SSE tests** and verify they fail because metrics and payload timestamp are absent.
- [x] **Step 3: Add `AuctionSseMetrics`; inject `Clock` and metrics into the manager; set `publishedAt` when the async broadcast begins; time each `emitter.send`; increment failures before removing a failed emitter.**
- [x] **Step 4: Run focused SSE tests** and verify they pass.

### Task 4: k6 delivery-latency trend

**Files:**
- Modify: `backend/src/test/k6/final-auction-load.js`
- Modify: `backend/src/test/k6/bid/auction-bid.js`
- Modify: `backend/src/test/k6/bid/README.md`

**Interfaces:**
- Consumes: SSE payload `published_at`.
- Produces: `auction_sse_delivery_latency` Trend only for auction event payloads carrying a valid timestamp, plus an invalid/missing timestamp counter.

- [x] **Step 1: Add an event parser in both auction SSE load scripts** that records the difference between local receipt time and `published_at`.
- [x] **Step 2: Count malformed or missing timestamps instead of failing an SSE connection.**
- [x] **Step 3: Document the Trend, its clock-synchronization requirement, and its exclusion of events without `published_at`.**

### Task 5: Verification and handoff

**Files:**
- Modify: `docs/eunki/2026-08-09-bid-load-test-metrics-plan.md`

- [ ] **Step 1: Run the focused auction metrics, bid service, and SSE test suites.**
- [ ] **Step 2: Run `./gradlew test` from `backend`.**
- [ ] **Step 3: Inspect the worktree diff and confirm only #286 files are staged.**
- [ ] **Step 4: Commit the plan and implementation with the required message body, referencing issue #286.**
