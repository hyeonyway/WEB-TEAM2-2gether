package com.dbidding.auction.metrics;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class AuctionMetrics {

    private static final Duration[] OPERATION_SLOS = {
            Duration.ofMillis(50),
            Duration.ofMillis(100),
            Duration.ofMillis(300),
            Duration.ofMillis(500),
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(5)
    };
    private static final Duration[] LOCK_SLOS = {
            Duration.ofMillis(1),
            Duration.ofMillis(5),
            Duration.ofMillis(10),
            Duration.ofMillis(50),
            Duration.ofMillis(100),
            Duration.ofMillis(500),
            Duration.ofSeconds(1),
            Duration.ofSeconds(3)
    };

    private final MeterRegistry registry;
    private final Map<BidResult, Timer> bidTimers = new EnumMap<>(BidResult.class);
    private final Map<CloseResult, Timer> closeTimers = new EnumMap<>(CloseResult.class);
    private final Map<LockOperation, Timer> lockTimers = new EnumMap<>(LockOperation.class);
    private final Map<BidStep, Timer> bidStepTimers = new EnumMap<>(BidStep.class);
    private final Timer bidCriticalSectionTimer;
    private final Timer bidFlushTimer;
    private final Counter auctionEndingTransitions;

    public AuctionMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (BidResult result : BidResult.values()) {
            bidTimers.put(result, operationTimer(
                    "dbidding.bid.duration",
                    "입찰 요청 전체 처리시간",
                    "result",
                    result.tag()
            ));
        }
        for (CloseResult result : CloseResult.values()) {
            closeTimers.put(result, operationTimer(
                    "dbidding.auction.close.duration",
                    "경매 종료 전체 처리시간",
                    "result",
                    result.tag()
            ));
        }
        for (LockOperation operation : LockOperation.values()) {
            lockTimers.put(operation, Timer.builder("dbidding.auction.lock.wait")
                    .description("경매 row 비관적 락 획득 대기시간")
                    .tag("operation", operation.tag())
                    .publishPercentileHistogram()
                    .serviceLevelObjectives(LOCK_SLOS)
                    .register(registry));
        }
        bidCriticalSectionTimer = Timer.builder("dbidding.bid.critical_section.duration")
                .description("경매 락 획득 이후 입찰 핵심 처리시간")
                .publishPercentileHistogram()
                .serviceLevelObjectives(OPERATION_SLOS)
                .register(registry);
        for (BidStep step : BidStep.values()) {
            bidStepTimers.put(step, Timer.builder("dbidding.bid.step.duration")
                    .description("입찰 하위 단계 처리시간")
                    .tag("step", step.tag())
                    .publishPercentileHistogram()
                    .serviceLevelObjectives(OPERATION_SLOS)
                    .register(registry));
        }
        bidFlushTimer = Timer.builder("dbidding.bid.db_flush.duration")
                .description("입찰 DB flush 처리시간")
                .publishPercentileHistogram()
                .serviceLevelObjectives(OPERATION_SLOS)
                .register(registry);
        auctionEndingTransitions = Counter.builder("dbidding.auction.ending.transitions")
                .description("경매가 시간 기준으로 ENDING 상태에 진입한 횟수")
                .register(registry);
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void finishBid(Timer.Sample sample, BidResult result) {
        sample.stop(bidTimers.get(result));
    }

    public void finishClose(Timer.Sample sample, CloseResult result) {
        sample.stop(closeTimers.get(result));
    }

    public void finishAuctionLockWait(Timer.Sample sample, LockOperation operation) {
        sample.stop(lockTimers.get(operation));
    }

    public Timer.Sample startBidCriticalSection() {
        return start();
    }

    public void finishBidCriticalSection(Timer.Sample sample) {
        sample.stop(bidCriticalSectionTimer);
    }

    public void recordBidStep(BidStep step, Runnable action) {
        bidStepTimers.get(step).record(action);
    }

    public <T> T recordBidStep(BidStep step, Supplier<T> action) {
        return bidStepTimers.get(step).record(action);
    }

    public Timer.Sample startBidFlush() {
        return start();
    }

    public void finishBidFlush(Timer.Sample sample) {
        sample.stop(bidFlushTimer);
    }

    public void recordEndingTransition() {
        auctionEndingTransitions.increment();
    }

    private Timer operationTimer(String name, String description, String tagName, String tagValue) {
        return Timer.builder(name)
                .description(description)
                .tag(tagName, tagValue)
                .publishPercentileHistogram()
                .serviceLevelObjectives(OPERATION_SLOS)
                .register(registry);
    }

    public enum BidResult {
        ACCEPTED("accepted"),
        REJECTED("rejected"),
        ERROR("error");

        private final String tag;

        BidResult(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }

    public enum CloseResult {
        WITH_WINNER("with_winner"),
        WITHOUT_TRADE("without_trade"),
        ERROR("error");

        private final String tag;

        CloseResult(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }

    public enum LockOperation {
        BID("bid"),
        CLOSE("close");

        private final String tag;

        LockOperation(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }

    public enum BidStep {
        OUTBID("outbid"),
        HOLD("hold"),
        SAVE("save");

        private final String tag;

        BidStep(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }
}
