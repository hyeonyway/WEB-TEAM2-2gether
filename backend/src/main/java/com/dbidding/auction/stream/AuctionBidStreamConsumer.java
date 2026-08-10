package com.dbidding.auction.stream;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.data.redis.connection.stream.RecordId;
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
    static final String STREAM_KEY = "auction:bid-events:v1";
    static final String GROUP = "auction-bid-persistence";
    static final String DLQ_KEY = "auction:bid-events:dlq:v1";
    static final String RETRY_KEY = "auction:bid-events:retry-count:v1";

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
            consumeOnce();
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

    private void consumeOnce() {
        List<MapRecord<String, Object, Object>> records = claimPending();
        if (records.isEmpty()) {
            records = redisTemplate.opsForStream().read(
                    Consumer.from(GROUP, consumerName),
                    StreamReadOptions.empty().count(properties.batchSize()).block(properties.block()),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
            );
        }
        if (records == null || records.isEmpty()) {
            return;
        }
        process(records);
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

    private List<MapRecord<String, Object, Object>> claimPending() {
        List<RecordId> ids = new ArrayList<>();
        for (PendingMessage pending : redisTemplate.opsForStream().pending(
                STREAM_KEY, GROUP, Range.unbounded(), properties.batchSize(), properties.claimIdle()
        )) {
            ids.add(pending.getId());
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        return redisTemplate.opsForStream().claim(
                STREAM_KEY, GROUP, consumerName, properties.claimIdle(), ids.toArray(RecordId[]::new)
        ).stream().filter(record -> !isPausedAuction(record)).toList();
    }

    private void process(List<MapRecord<String, Object, Object>> records) {
        try {
            List<BidAcceptedStreamEvent> events = records.stream()
                    .map(record -> BidAcceptedStreamEvent.from(record.getId().getValue(), stringValues(record.getValue())))
                    .toList();
            persistenceService.persistAll(events);
            acknowledge(records);
            records.forEach(record -> redisTemplate.opsForHash().delete(RETRY_KEY, record.getId().getValue()));
            events.forEach(event -> pausedAuctionRegistry.resume(event.auctionId()));
            meterRegistry.counter("auction.bid.stream.persisted").increment(records.size());
        } catch (RuntimeException exception) {
            // 배치 트랜잭션 실패가 정상 레코드까지 DLQ로 전파되지 않도록 개별 트랜잭션으로 분리한다.
            records.forEach(this::processOne);
        }
    }

    private void processOne(MapRecord<String, Object, Object> record) {
        BidAcceptedStreamEvent event = null;
        try {
            event = BidAcceptedStreamEvent.from(
                    record.getId().getValue(), stringValues(record.getValue())
            );
            persistenceService.persistAll(List.of(event));
            acknowledge(List.of(record));
            redisTemplate.opsForHash().delete(RETRY_KEY, record.getId().getValue());
            pausedAuctionRegistry.resume(event.auctionId());
            meterRegistry.counter("auction.bid.stream.persisted").increment();
        } catch (BidStreamVersionGapException exception) {
            pausedAuctionRegistry.pause(event, exception);
            meterRegistry.counter("auction.bid.stream.auction.paused").increment();
        } catch (InvalidBidStreamEventException exception) {
            moveToDlq(record, exception);
        } catch (RuntimeException exception) {
            retryOrDlq(record, exception);
        }
    }

    private boolean isPausedAuction(MapRecord<String, Object, Object> record) {
        try {
            BidAcceptedStreamEvent event = BidAcceptedStreamEvent.from(
                    record.getId().getValue(), stringValues(record.getValue())
            );
            return pausedAuctionRegistry.isPaused(event.auctionId());
        } catch (InvalidBidStreamEventException ignored) {
            return false;
        }
    }

    private void retryOrDlq(MapRecord<String, Object, Object> record, RuntimeException exception) {
        Long attempts = redisTemplate.opsForHash().increment(RETRY_KEY, record.getId().getValue(), 1);
        if (attempts != null && attempts >= properties.maxRetries()) {
            moveToDlq(record, exception);
            return;
        }
        meterRegistry.counter("auction.bid.stream.retry").increment();
        log.warn("event=auction.bid.stream.retry streamId={} retryCount={}", record.getId().getValue(), attempts, exception);
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
        acknowledge(List.of(record));
        redisTemplate.opsForHash().delete(RETRY_KEY, record.getId().getValue());
        meterRegistry.counter("auction.bid.stream.dlq").increment();
        log.error("event=auction.bid.stream.dlq streamId={}", record.getId().getValue(), exception);
    }

    private void acknowledge(List<MapRecord<String, Object, Object>> records) {
        redisTemplate.opsForStream().acknowledge(
                STREAM_KEY, GROUP, records.stream().map(MapRecord::getId).toArray(RecordId[]::new)
        );
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
}
