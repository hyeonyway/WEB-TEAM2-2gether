package com.dbidding.auction.stream;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.dbidding.auction.domain.AuctionBidEventInbox;
import com.dbidding.auction.repository.AuctionBidEventInboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 단일 Redis Stream을 순서대로 MySQL projection으로 전달하는 전역 단일 consumer다.
 *
 * <p>leader lock은 여러 애플리케이션 인스턴스가 떠도 활성 worker를 하나로 제한한다. 이 consumer는
 * 한 번에 한 이벤트만 처리하고, 처리 결론이 난 entry는 ACK와 XDEL로 즉시 제거한다.
 * inbox가 projection 결과와 오류 원인의 영속 근거다.</p>
 */
@Slf4j
@Component
@Profile("redis")
@RequiredArgsConstructor
public class AuctionBidStreamConsumer implements SmartLifecycle {
    static final String STREAM_KEY = "event:timeline";
    static final String GROUP = "auction-timeline-persistence";
    static final String CONSUMER_NAME = "auction-timeline-single";

    private final StringRedisTemplate redisTemplate;
    private final AuctionBidStreamPersistenceService persistenceService;
    private final AuctionBidStreamProperties properties;
    private final AuctionBidStreamConsumerLeaderLock leaderLock;
    private final AuctionBidEventInboxRepository inboxRepository;
    private final ObjectMapper objectMapper;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("auction-timeline-single-", 0).factory()
    );
    private final String consumerName = CONSUMER_NAME;
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
            if (projectOldestPending()) continue;
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
            acknowledgeAndDelete(record);
            return;
        }
        persistenceService.recordPending(event, serialize(values));
        acknowledgeAndDelete(record);
    }

    /** Redis Stream은 inbox 적재만 담당하고, 실제 projection은 DB inbox 순서만 따른다. */
    private boolean projectOldestPending() {
        if (persistenceService.hasProjectionError()) return false;
        AuctionBidEventInbox inbox = inboxRepository.findFirstByProjectionStatusOrderByIdAsc(
                com.dbidding.auction.domain.AuctionBidEventProjectionStatus.PENDING).orElse(null);
        if (inbox == null) return false;
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
        return true;
    }

    private String serialize(Map<String, String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Stream payload 직렬화에 실패했습니다.", exception);
        }
    }

    private RuntimeException projectWithRetry(AuctionWalletTimelineEvent event) {
        RuntimeException failure = null;
        for (int attempt = 0; attempt < properties.maxRetries(); attempt++) {
            try {
                persistenceService.recordProjectionAttempt(event.streamId());
                persistenceService.project(event);
                return null;
            } catch (RuntimeException exception) {
                failure = exception;
                if (!isTransient(exception) || attempt + 1 == properties.maxRetries()) return failure;
                try {
                    Thread.sleep(Duration.ofSeconds(1L << attempt));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return exception;
                }
            }
        }
        return failure;
    }

    private boolean isTransient(RuntimeException exception) {
        return exception instanceof TransientDataAccessException
                || exception instanceof RecoverableDataAccessException
                || exception instanceof CannotCreateTransactionException;
    }

    void acknowledge(MapRecord<String, Object, Object> record) {
        redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP, record.getId());
    }

    void acknowledgeAndDelete(MapRecord<String, Object, Object> record) {
        acknowledge(record);
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
