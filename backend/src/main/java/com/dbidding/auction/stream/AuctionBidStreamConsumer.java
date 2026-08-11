package com.dbidding.auction.stream;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("redis")
@RequiredArgsConstructor
public class AuctionBidStreamConsumer {
    static final String STREAM_KEY = "auction:timeline-events";
    static final String GROUP = "auction-timeline-persistence";
    static final String DLQ_KEY = "auction:timeline-events:dlq";
    static final String RETRY_KEY = "auction:timeline-events:retry-count";

    private final StringRedisTemplate redisTemplate;
    private final AuctionBidStreamPersistenceService persistenceService;
    private final AuctionBidStreamProperties properties;
    private final AuctionBidStreamConsumerLeaderLock leaderLock;
    private final AuctionBidStreamPausedAuctionRegistry pausedAuctionRegistry;
    private final MeterRegistry meterRegistry;
    private final String consumerName = "auction-bid-" + UUID.randomUUID();

    @PostConstruct
    void createGroup() {
        try {
            redisTemplate.execute((RedisCallback<String>) connection -> connection.streamCommands().xGroupCreate(
                    STREAM_KEY.getBytes(StandardCharsets.UTF_8), GROUP, ReadOffset.from("0-0"), true
            ));
        } catch (DataAccessException exception) {
            if (!isExistingGroup(exception)) {
                throw exception;
            }
        }
    }

    private boolean isExistingGroup(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }
        }
        return false;
    }

    @Scheduled(fixedDelayString = "${app.auction.redis-bid.poll-delay:100ms}")
    public void consume() {
        if (!leaderLock.tryAcquire()) {
            return;
        }
        try {
            for (int processed = 0; processed < properties.maxRecordsPerRun(); processed++) {
                if (!consumeOnce()) {
                    return;
                }
            }
        } catch (DataAccessException exception) {
            if (!isMissingGroup(exception)) {
                throw exception;
            }
            createGroup();
            log.info("event=auction.bid.stream.group.recreated streamKey={} group={}", STREAM_KEY, GROUP);
        } finally {
            leaderLock.releaseAfterRun();
        }
    }

    private boolean consumeOnce() {
        PendingClaim pendingClaim = claimPending();
        if (pendingClaim.blocksNewEvents()) {
            return false;
        }
        MapRecord<String, Object, Object> record = pendingClaim.record();
        if (record != null && isPausedAuction(record)) {
            // 하나의 전역 타임라인에서는 앞선 버전 단절을 건너뛰고 뒤 이벤트를 처리하면 안 된다.
            return false;
        }
        if (record == null) {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    Consumer.from(GROUP, consumerName),
                    StreamReadOptions.empty().count(1).block(properties.block()),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
            );
            record = records == null || records.isEmpty() ? null : records.getFirst();
        }
        if (record == null) {
            return false;
        }
        return processOne(record);
    }

    private boolean isMissingGroup(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains("NOGROUP")) {
                return true;
            }
        }
        return false;
    }

    private PendingClaim claimPending() {
        java.util.Iterator<PendingMessage> pending = redisTemplate.opsForStream().pending(
                STREAM_KEY, GROUP, Range.unbounded(), 1
        ).iterator();
        if (!pending.hasNext()) {
            return PendingClaim.none();
        }
        PendingMessage message = pending.next();
        boolean ownMessage = consumerName.equals(message.getConsumerName());
        if (!ownMessage && message.getElapsedTimeSinceLastDelivery().compareTo(properties.claimIdle()) < 0) {
            return PendingClaim.blocked();
        }
        java.time.Duration minimumIdle = ownMessage ? java.time.Duration.ZERO : properties.claimIdle();
        List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream().claim(
                STREAM_KEY, GROUP, consumerName, minimumIdle, message.getId()
        );
        if (claimed == null || claimed.isEmpty()) {
            return PendingClaim.blocked();
        }
        return PendingClaim.claimed(claimed.getFirst());
    }

    private boolean processOne(MapRecord<String, Object, Object> record) {
        AuctionWalletTimelineEvent event = null;
        try {
            event = AuctionWalletTimelineEvent.from(
                    record.getId().getValue(), stringValues(record.getValue())
            );
            persistenceService.persist(event);
            acknowledge(record);
            redisTemplate.opsForHash().delete(RETRY_KEY, record.getId().getValue());
            if (event instanceof BidAcceptedStreamEvent bid) {
                pausedAuctionRegistry.resume(bid.auctionId());
            }
            meterRegistry.counter("auction.bid.stream.persisted").increment();
            return true;
        } catch (BidStreamVersionGapException exception) {
            pausedAuctionRegistry.pause((BidAcceptedStreamEvent) event, exception);
            meterRegistry.counter("auction.bid.stream.auction.paused").increment();
            return false;
        } catch (InvalidBidStreamEventException exception) {
            moveToDlq(record, exception);
            return true;
        } catch (RuntimeException exception) {
            return retryOrDlq(record, exception);
        }
    }

    private boolean isPausedAuction(MapRecord<String, Object, Object> record) {
        try {
            AuctionWalletTimelineEvent event = AuctionWalletTimelineEvent.from(
                    record.getId().getValue(), stringValues(record.getValue())
            );
            return event instanceof BidAcceptedStreamEvent bid && pausedAuctionRegistry.isPaused(bid.auctionId());
        } catch (InvalidBidStreamEventException ignored) {
            return false;
        }
    }

    private boolean retryOrDlq(MapRecord<String, Object, Object> record, RuntimeException exception) {
        Long attempts = redisTemplate.opsForHash().increment(RETRY_KEY, record.getId().getValue(), 1);
        if (attempts != null && attempts >= properties.maxRetries()) {
            moveToDlq(record, exception);
            return true;
        }
        meterRegistry.counter("auction.bid.stream.retry").increment();
        log.warn("event=auction.bid.stream.retry streamId={} retryCount={}", record.getId().getValue(), attempts, exception);
        return false;
    }

    private void moveToDlq(MapRecord<String, Object, Object> record, RuntimeException exception) {
        redisTemplate.opsForStream().add(DLQ_KEY, Map.of(
                "originalStreamId", record.getId().getValue(),
                "payload", payload(stringValues(record.getValue())),
                "failureType", exception.getClass().getSimpleName(),
                "failureMessage", String.valueOf(exception.getMessage()),
                "failedAt", Instant.now().toString(),
                "retryCount", String.valueOf(redisTemplate.opsForHash().get(RETRY_KEY, record.getId().getValue()))
        ));
        acknowledge(record);
        redisTemplate.opsForHash().delete(RETRY_KEY, record.getId().getValue());
        meterRegistry.counter("auction.bid.stream.dlq").increment();
        log.error("event=auction.bid.stream.dlq streamId={}", record.getId().getValue(), exception);
    }

    private void acknowledge(MapRecord<String, Object, Object> record) {
        Long acknowledged = redisTemplate.opsForStream().acknowledge(
                STREAM_KEY, GROUP, record.getId()
        );
        if (acknowledged == null || acknowledged == 0) {
            return;
        }
        try {
            redisTemplate.opsForStream().delete(STREAM_KEY, record.getId());
            meterRegistry.counter("auction.bid.stream.deleted").increment();
        } catch (RuntimeException exception) {
            // DB 반영과 ACK는 이미 끝났다. 삭제 실패를 재시도로 취급하면 같은 DB 이벤트를 DLQ로 오염시킨다.
            meterRegistry.counter("auction.bid.stream.delete.failed").increment();
            log.error("event=auction.bid.stream.delete.failed streamId={}", record.getId().getValue(), exception);
        }
    }

    private Map<String, String> stringValues(Map<Object, Object> values) {
        return values.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                entry -> String.valueOf(entry.getKey()), entry -> String.valueOf(entry.getValue())
        ));
    }

    private String payload(Map<String, String> values) {
        return values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private record PendingClaim(MapRecord<String, Object, Object> record, boolean blocksNewEvents) {
        static PendingClaim none() {
            return new PendingClaim(null, false);
        }

        static PendingClaim blocked() {
            return new PendingClaim(null, true);
        }

        static PendingClaim claimed(MapRecord<String, Object, Object> record) {
            return new PendingClaim(record, false);
        }
    }
}
