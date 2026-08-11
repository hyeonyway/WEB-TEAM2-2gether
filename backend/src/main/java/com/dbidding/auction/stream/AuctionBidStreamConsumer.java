package com.dbidding.auction.stream;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import com.dbidding.auction.domain.AuctionBidEventInbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
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
import org.springframework.stereotype.Component;

/**
 * Redis가 XREADGROUP BLOCK 요청을 깨우는 동안만 대기하는 Stream worker다. projection 실패도
 * inbox에 ERROR/PENDING으로 보존한 뒤 ACK하므로 Stream을 전역 pause하거나 DLQ로 운영하지 않는다.
 */
@Slf4j
@Component
@Profile("redis")
@RequiredArgsConstructor
public class AuctionBidStreamConsumer implements SmartLifecycle {
    static final String STREAM_KEY = "auction:timeline-events";
    static final String GROUP = "auction-timeline-persistence";

    private final StringRedisTemplate redisTemplate;
    private final AuctionBidStreamPersistenceService persistenceService;
    private final AuctionBidStreamProperties properties;
    private final AuctionBidStreamConsumerLeaderLock leaderLock;
    private final ExecutorService worker = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    private final String consumerName = "auction-bid-" + UUID.randomUUID();
    private volatile boolean running;

    @PostConstruct
    void createGroup() {
        try {
            redisTemplate.execute((RedisCallback<String>) connection -> connection.streamCommands().xGroupCreate(
                    STREAM_KEY.getBytes(StandardCharsets.UTF_8), GROUP, ReadOffset.from("0-0"), true
            ));
        } catch (DataAccessException exception) {
            if (!isExistingGroup(exception)) throw exception;
        }
    }

    @Override
    public synchronized void start() {
        if (running) return;
        running = true;
        worker.submit(this::runWorker);
    }

    private void runWorker() {
        while (running) {
            try {
                if (!leaderLock.isLeader() && !leaderLock.tryAcquire()) {
                    Thread.sleep(Duration.ofSeconds(1));
                    continue;
                }
                consumeUntilIdle();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException exception) {
                // DB 수신 기록 자체가 실패한 경우에는 ACK하지 않아 다음 blocking read에서 다시 시도한다.
                log.error("event=auction.bid.stream.worker.failed", exception);
            }
        }
    }

    private void consumeUntilIdle() {
        for (int processed = 0; running && leaderLock.isLeader() && processed < properties.maxRecordsPerRun(); processed++) {
            MapRecord<String, Object, Object> record = claimPending();
            if (record == null) {
                List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                        Consumer.from(GROUP, consumerName),
                        StreamReadOptions.empty().count(1).block(properties.block()),
                        StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
                );
                record = records == null || records.isEmpty() ? null : records.getFirst();
            }
            if (record == null) return;
            receiveAndAcknowledge(record);
        }
    }

    private MapRecord<String, Object, Object> claimPending() {
        java.util.Iterator<PendingMessage> pending = redisTemplate.opsForStream().pending(
                STREAM_KEY, GROUP, Range.unbounded(), 1).iterator();
        if (!pending.hasNext()) return null;
        PendingMessage message = pending.next();
        boolean ownMessage = consumerName.equals(message.getConsumerName());
        if (!ownMessage && message.getElapsedTimeSinceLastDelivery().compareTo(properties.claimIdle()) < 0) return null;
        List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream().claim(
                STREAM_KEY, GROUP, consumerName, ownMessage ? Duration.ZERO : properties.claimIdle(), message.getId());
        return claimed == null || claimed.isEmpty() ? null : claimed.getFirst();
    }

    private void receiveAndAcknowledge(MapRecord<String, Object, Object> record) {
        Map<String, String> values = stringValues(record.getValue());
        AuctionWalletTimelineEvent event;
        try {
            event = AuctionWalletTimelineEvent.from(record.getId().getValue(), values);
        } catch (InvalidBidStreamEventException exception) {
            AuctionBidEventInbox inbox = persistenceService.recordMalformed(record.getId().getValue(), values);
            if (!persistenceService.hasProjectionError() && persistenceService.markError(inbox.getStreamId(), exception)) {
                log.error("event=auction.bid.stream.projection.error streamId={} malformed=true", inbox.getStreamId(), exception);
            }
            acknowledge(record);
            return;
        }
        persistenceService.recordPending(event);
        if (!persistenceService.hasProjectionError()) {
            try {
                persistenceService.project(event);
                persistenceService.markProcessed(event.streamId());
            } catch (RuntimeException exception) {
                if (persistenceService.markError(event.streamId(), exception)) {
                    // ERROR 로그는 기존 Slack appender가 최초 projection 장애를 알린다.
                    log.error("event=auction.bid.stream.projection.error streamId={} auctionId={}",
                            event.streamId(), event instanceof BidAcceptedStreamEvent bid ? bid.auctionId() : null, exception);
                }
            }
        }
        acknowledge(record);
    }

    private void acknowledge(MapRecord<String, Object, Object> record) {
        redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP, record.getId());
        redisTemplate.opsForStream().delete(STREAM_KEY, record.getId());
    }

    private Map<String, String> stringValues(Map<Object, Object> values) {
        return values.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                entry -> String.valueOf(entry.getKey()), entry -> String.valueOf(entry.getValue())));
    }

    private boolean isExistingGroup(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains("BUSYGROUP")) return true;
        }
        return false;
    }

    @Override public synchronized void stop() { running = false; leaderLock.releaseAfterRun(); worker.shutdownNow(); }
    @PreDestroy void shutdown() { stop(); }
    @Override public boolean isRunning() { return running; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MAX_VALUE - 100; }
}
