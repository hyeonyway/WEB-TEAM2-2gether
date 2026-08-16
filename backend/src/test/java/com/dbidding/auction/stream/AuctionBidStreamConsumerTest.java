package com.dbidding.auction.stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import com.dbidding.auction.domain.AuctionTimelineEvent;
import com.dbidding.auction.repository.AuctionTimelineEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class AuctionBidStreamConsumerTest {
    @Test
    @SuppressWarnings("unchecked")
    void 처리_완료_이벤트는_ACK_후_Stream에서_삭제한다() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        RecordId recordId = RecordId.of("1-0");
        org.mockito.Mockito.when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        org.mockito.Mockito.when(record.getId()).thenReturn(recordId);

        AuctionBidStreamConsumer consumer = new AuctionBidStreamConsumer(
                redisTemplate,
                mock(AuctionBidStreamPersistenceService.class),
                new AuctionBidStreamProperties(Duration.ofSeconds(1), Duration.ofSeconds(30), 3, Duration.ofMinutes(5), 100),
                mock(AuctionBidStreamConsumerLeaderLock.class),
                mock(AuctionTimelineEventRepository.class),
                new ObjectMapper()
        );

        consumer.acknowledgeAndDelete(record);

        verify(streamOperations).acknowledge("event:timeline", "auction-timeline-persistence", recordId);
        verify(streamOperations).delete("event:timeline", recordId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void malformed_이벤트는_오류로_기록한_뒤_ACK와_삭제를_수행한다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        AuctionBidStreamPersistenceService persistence = mock(AuctionBidStreamPersistenceService.class);
        AuctionTimelineEvent inbox = mock(AuctionTimelineEvent.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(record.getId()).thenReturn(RecordId.of("bad-1"));
        when(record.getValue()).thenReturn(Map.of("eventType", "unknown"));
        when(persistence.recordMalformed("bad-1", Map.of("eventType", "unknown"))).thenReturn(inbox);
        when(inbox.getStreamId()).thenReturn("bad-1");
        when(persistence.hasProjectionError()).thenReturn(false);
        when(persistence.markError(org.mockito.ArgumentMatchers.eq("bad-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        invoke(consumer(redisTemplate, persistence), "receiveAndAcknowledge", record);

        verify(persistence).recordMalformed("bad-1", Map.of("eventType", "unknown"));
        verify(persistence).markError(org.mockito.ArgumentMatchers.eq("bad-1"), org.mockito.ArgumentMatchers.any(InvalidBidStreamEventException.class));
        verify(streamOperations).acknowledge("event:timeline", "auction-timeline-persistence", RecordId.of("bad-1"));
        verify(streamOperations).delete("event:timeline", RecordId.of("bad-1"));
    }

    @Test
    void transient_예외는_재시도_후_projection에_성공한다() throws Exception {
        AuctionBidStreamPersistenceService persistence = mock(AuctionBidStreamPersistenceService.class);
        WalletStateChangedStreamEvent event = walletEvent("retry-1");
        doThrow(new org.springframework.dao.TransientDataAccessResourceException("temporary"))
                .doNothing().when(persistence).project(event);

        Object failure = invoke(consumer(mock(StringRedisTemplate.class), persistence), "projectWithRetry", event);

        org.assertj.core.api.Assertions.assertThat(failure).isNull();
        verify(persistence, times(2)).recordProjectionAttempt("retry-1");
        verify(persistence, times(2)).project(event);
    }

    @Test
    void transient_retry_sleep중_interrupt되면_현재_실패를_반환한다() throws Exception {
        AuctionBidStreamPersistenceService persistence = mock(AuctionBidStreamPersistenceService.class);
        WalletStateChangedStreamEvent event = walletEvent("retry-interrupt");
        doThrow(new org.springframework.dao.TransientDataAccessResourceException("temporary")).when(persistence).project(event);
        AuctionBidStreamConsumer consumer = new AuctionBidStreamConsumer(mock(StringRedisTemplate.class), persistence,
                new AuctionBidStreamProperties(Duration.ofSeconds(1), Duration.ofSeconds(1), 3, Duration.ofSeconds(1), 1),
                mock(AuctionBidStreamConsumerLeaderLock.class), mock(AuctionTimelineEventRepository.class), new ObjectMapper());
        java.util.concurrent.atomic.AtomicReference<Object> result = new java.util.concurrent.atomic.AtomicReference<>();
        Thread thread = new Thread(() -> { try { result.set(invoke(consumer, "projectWithRetry", event)); } catch (Exception exception) { result.set(exception); } });
        thread.start();
        Thread.sleep(50);
        thread.interrupt();
        thread.join(1000);
        org.assertj.core.api.Assertions.assertThat(result.get()).isInstanceOf(org.springframework.dao.TransientDataAccessResourceException.class);
    }

    @Test
    void transient_예외가_최대재시도까지_실패하면_마지막_실패를_반환한다() throws Exception {
        AuctionBidStreamPersistenceService persistence = mock(AuctionBidStreamPersistenceService.class);
        WalletStateChangedStreamEvent event = walletEvent("retry-last");
        org.springframework.dao.TransientDataAccessResourceException failure = new org.springframework.dao.TransientDataAccessResourceException("temporary");
        doThrow(failure).when(persistence).project(event);
        AuctionBidStreamConsumer consumer = new AuctionBidStreamConsumer(mock(StringRedisTemplate.class), persistence,
                new AuctionBidStreamProperties(Duration.ZERO, Duration.ofSeconds(1), 1, Duration.ofSeconds(1), 1),
                mock(AuctionBidStreamConsumerLeaderLock.class), mock(AuctionTimelineEventRepository.class), new ObjectMapper());

        org.assertj.core.api.Assertions.assertThat(invoke(consumer, "projectWithRetry", event)).isSameAs(failure);
    }

    @Test
    void transient_판정은_모든_데이터접근_예외_계열을_지원한다() throws Exception {
        AuctionBidStreamConsumer consumer = consumer(mock(StringRedisTemplate.class), mock(AuctionBidStreamPersistenceService.class));
        org.assertj.core.api.Assertions.assertThat(invoke(consumer, "isTransient", (Object) new org.springframework.dao.TransientDataAccessResourceException("x"))).isEqualTo(true);
        org.assertj.core.api.Assertions.assertThat(invoke(consumer, "isTransient", (Object) new org.springframework.dao.RecoverableDataAccessException("x"))).isEqualTo(true);
        org.assertj.core.api.Assertions.assertThat(invoke(consumer, "isTransient", (Object) new org.springframework.transaction.CannotCreateTransactionException("x"))).isEqualTo(true);
        org.assertj.core.api.Assertions.assertThat(invoke(consumer, "isTransient", (Object) new IllegalStateException("x"))).isEqualTo(false);
    }

    @Test
    void 비재시도_예외는_한번만_projection을_시도하고_반환한다() throws Exception {
        AuctionBidStreamPersistenceService persistence = mock(AuctionBidStreamPersistenceService.class);
        WalletStateChangedStreamEvent event = walletEvent("failure-1");
        IllegalArgumentException exception = new IllegalArgumentException("invalid");
        doThrow(exception).when(persistence).project(event);

        Object failure = invoke(consumer(mock(StringRedisTemplate.class), persistence), "projectWithRetry", event);

        org.assertj.core.api.Assertions.assertThat(failure).isSameAs(exception);
        verify(persistence).recordProjectionAttempt("failure-1");
        verify(persistence).project(event);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 유효한_지갑_이벤트는_inbox에_저장한_뒤_ACK와_삭제를_수행한다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        AuctionBidStreamPersistenceService persistence = mock(AuctionBidStreamPersistenceService.class);
        Map<String, String> values = Map.of(
                "schemaVersion", "2", "eventId", "8ef477e7-1c80-42ea-a7af-8d8ea9c6d411", "eventType", "wallet.charged.v1",
                "userId", "1", "walletVersion", "2", "availableBalance", "10000", "frozenBalance", "0",
                "occurredAt", "2026-08-10T12:00:00Z");
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(record.getId()).thenReturn(RecordId.of("valid-1"));
        when(record.getValue()).thenReturn((Map) values);

        invoke(consumer(redisTemplate, persistence), "receiveAndAcknowledge", record);

        verify(persistence).recordPending(org.mockito.ArgumentMatchers.any(WalletStateChangedStreamEvent.class), org.mockito.ArgumentMatchers.anyString());
        verify(streamOperations).acknowledge("event:timeline", "auction-timeline-persistence", RecordId.of("valid-1"));
        verify(streamOperations).delete("event:timeline", RecordId.of("valid-1"));
    }

    @Test
    void projection_오류가_있거나_대기_inbox가_없으면_기존_inbox를_처리하지_않는다() throws Exception {
        AuctionBidStreamPersistenceService persistence = mock(AuctionBidStreamPersistenceService.class);
        AuctionTimelineEventRepository inbox = mock(AuctionTimelineEventRepository.class);
        AuctionBidStreamConsumer consumer = new AuctionBidStreamConsumer(mock(StringRedisTemplate.class), persistence,
                new AuctionBidStreamProperties(Duration.ofMillis(1), Duration.ofSeconds(1), 2, Duration.ofSeconds(1), 10),
                mock(AuctionBidStreamConsumerLeaderLock.class), inbox, new ObjectMapper());
        when(persistence.hasProjectionError()).thenReturn(true, false);
        when(inbox.findFirstByProjectionStatusOrderByIdAsc(com.dbidding.auction.domain.AuctionBidEventProjectionStatus.PENDING))
                .thenReturn(java.util.Optional.empty());

        org.assertj.core.api.Assertions.assertThat(invoke(consumer, "projectOldestPending")).isEqualTo(false);
        org.assertj.core.api.Assertions.assertThat(invoke(consumer, "projectOldestPending")).isEqualTo(false);
        verify(inbox).findFirstByProjectionStatusOrderByIdAsc(com.dbidding.auction.domain.AuctionBidEventProjectionStatus.PENDING);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pending_메시지가_없으면_claim하지_않는다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        PendingMessages pending = mock(PendingMessages.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.pending(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L))).thenReturn(pending);
        when(pending.iterator()).thenReturn(java.util.Collections.emptyIterator());

        org.assertj.core.api.Assertions.assertThat(invoke(consumer(redisTemplate, mock(AuctionBidStreamPersistenceService.class)), "claimPending")).isNull();

        verify(streamOperations, org.mockito.Mockito.never()).claim(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void 현재_consumer의_pending_메시지는_즉시_claim한다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        PendingMessages pending = mock(PendingMessages.class);
        PendingMessage message = mock(PendingMessage.class);
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        RecordId id = RecordId.of("2-0");
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.pending(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L))).thenReturn(pending);
        when(pending.iterator()).thenReturn(java.util.List.of(message).iterator());
        when(message.getConsumerName()).thenReturn("auction-timeline-single");
        when(message.getId()).thenReturn(id);
        when(streamOperations.claim(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Duration.ZERO), org.mockito.ArgumentMatchers.eq(id)))
                .thenReturn(java.util.List.of(record));

        org.assertj.core.api.Assertions.assertThat(invoke(consumer(redisTemplate, mock(AuctionBidStreamPersistenceService.class)), "claimPending")).isSameAs(record);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 다른_consumer의_idle_초과_pending은_claim한다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        PendingMessages pending = mock(PendingMessages.class);
        PendingMessage message = mock(PendingMessage.class);
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        RecordId id = RecordId.of("3-0");
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.pending(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L))).thenReturn(pending);
        when(pending.iterator()).thenReturn(java.util.List.of(message).iterator());
        when(message.getConsumerName()).thenReturn("other-consumer");
        when(message.getElapsedTimeSinceLastDelivery()).thenReturn(Duration.ofSeconds(2));
        when(message.getId()).thenReturn(id);
        when(streamOperations.claim(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(1)), org.mockito.ArgumentMatchers.eq(id)))
                .thenReturn(java.util.List.of(record));

        org.assertj.core.api.Assertions.assertThat(invoke(consumer(redisTemplate, mock(AuctionBidStreamPersistenceService.class)), "claimPending")).isSameAs(record);
    }

    @Test
    void pending_inbox의_유효한_payload는_projection_후_처리완료로_표시한다() throws Exception {
        AuctionBidStreamPersistenceService persistence = mock(AuctionBidStreamPersistenceService.class);
        AuctionTimelineEventRepository inbox = mock(AuctionTimelineEventRepository.class);
        AuctionTimelineEvent pending = new AuctionTimelineEvent("inbox-1", null, null, "wallet.charged.v1", 2,
                "{\"schemaVersion\":\"2\",\"eventId\":\"8ef477e7-1c80-42ea-a7af-8d8ea9c6d411\",\"eventType\":\"wallet.charged.v1\",\"userId\":\"1\",\"walletVersion\":\"2\",\"availableBalance\":\"10000\",\"frozenBalance\":\"0\",\"occurredAt\":\"2026-08-10T12:00:00Z\"}",
                Instant.parse("2026-08-10T12:00:00Z"), Instant.now());
        AuctionBidStreamConsumer consumer = new AuctionBidStreamConsumer(mock(StringRedisTemplate.class), persistence,
                new AuctionBidStreamProperties(Duration.ofMillis(1), Duration.ofSeconds(1), 1, Duration.ofSeconds(1), 10),
                mock(AuctionBidStreamConsumerLeaderLock.class), inbox, new ObjectMapper());
        when(persistence.hasProjectionError()).thenReturn(false);
        when(inbox.findFirstByProjectionStatusOrderByIdAsc(com.dbidding.auction.domain.AuctionBidEventProjectionStatus.PENDING))
                .thenReturn(java.util.Optional.of(pending));
        when(persistence.markError(org.mockito.ArgumentMatchers.eq("inbox-bad"), org.mockito.ArgumentMatchers.any())).thenReturn(true);

        org.assertj.core.api.Assertions.assertThat(invoke(consumer, "projectOldestPending")).isEqualTo(true);

        verify(persistence).recordProjectionAttempt("inbox-1");
        verify(persistence).project(org.mockito.ArgumentMatchers.any(WalletStateChangedStreamEvent.class));
        verify(persistence).markProcessed("inbox-1");
    }

    @Test
    void pending_inbox의_손상된_payload는_projection_오류로_표시한다() throws Exception {
        AuctionBidStreamPersistenceService persistence = mock(AuctionBidStreamPersistenceService.class);
        AuctionTimelineEventRepository inbox = mock(AuctionTimelineEventRepository.class);
        AuctionTimelineEvent pending = new AuctionTimelineEvent("inbox-bad", null, null, "wallet.charged.v1", 2, "not-json",
                Instant.parse("2026-08-10T12:00:00Z"), Instant.now());
        AuctionBidStreamConsumer consumer = new AuctionBidStreamConsumer(mock(StringRedisTemplate.class), persistence,
                new AuctionBidStreamProperties(Duration.ofMillis(1), Duration.ofSeconds(1), 1, Duration.ofSeconds(1), 10),
                mock(AuctionBidStreamConsumerLeaderLock.class), inbox, new ObjectMapper());
        when(persistence.hasProjectionError()).thenReturn(false);
        when(inbox.findFirstByProjectionStatusOrderByIdAsc(com.dbidding.auction.domain.AuctionBidEventProjectionStatus.PENDING))
                .thenReturn(java.util.Optional.of(pending));

        org.assertj.core.api.Assertions.assertThat(invoke(consumer, "projectOldestPending")).isEqualTo(true);

        verify(persistence).markError(org.mockito.ArgumentMatchers.eq("inbox-bad"), org.mockito.ArgumentMatchers.any(IllegalStateException.class));
    }

    @Test
    void lifecycle은_시작_상태와_종료_상태를_정상적으로_전환한다() {
        AuctionBidStreamConsumerLeaderLock lock = mock(AuctionBidStreamConsumerLeaderLock.class);
        AuctionBidStreamConsumer consumer = new AuctionBidStreamConsumer(mock(StringRedisTemplate.class), mock(AuctionBidStreamPersistenceService.class),
                new AuctionBidStreamProperties(Duration.ofMillis(1), Duration.ofSeconds(1), 1, Duration.ofSeconds(1), 1),
                lock, mock(AuctionTimelineEventRepository.class), new ObjectMapper());

        consumer.start();
        org.assertj.core.api.Assertions.assertThat(consumer.isRunning()).isTrue();
        org.assertj.core.api.Assertions.assertThat(consumer.isAutoStartup()).isTrue();
        org.assertj.core.api.Assertions.assertThat(consumer.getPhase()).isEqualTo(Integer.MAX_VALUE - 100);
        consumer.stop();
        consumer.shutdown();

        org.assertj.core.api.Assertions.assertThat(consumer.isRunning()).isFalse();
        verify(lock, org.mockito.Mockito.atLeastOnce()).releaseAfterRun();
    }

    @Test
    @SuppressWarnings("unchecked")
    void group_생성은_Redis_callback을_실행한다() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.any(org.springframework.data.redis.core.RedisCallback.class))).thenReturn(null);
        AuctionBidStreamConsumer consumer = consumer(redisTemplate, mock(AuctionBidStreamPersistenceService.class));

        consumer.createGroup();

        verify(redisTemplate).execute(org.mockito.ArgumentMatchers.any(org.springframework.data.redis.core.RedisCallback.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void group_생성시_BUSYGROUP_오류는_무시하고_다른_오류는_전파한다() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.any(org.springframework.data.redis.core.RedisCallback.class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("BUSYGROUP exists"));
        consumer(redisTemplate, mock(AuctionBidStreamPersistenceService.class)).createGroup();
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.any(org.springframework.data.redis.core.RedisCallback.class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("redis down"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer(redisTemplate, mock(AuctionBidStreamPersistenceService.class)).createGroup())
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
    }

    @Test
    void stream_payload_직렬화_실패는_IllegalStateException으로_변환한다() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = mock(com.fasterxml.jackson.databind.ObjectMapper.class);
        when(mapper.writeValueAsString(org.mockito.ArgumentMatchers.anyMap()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("broken") { });
        AuctionBidStreamConsumer consumer = new AuctionBidStreamConsumer(mock(StringRedisTemplate.class), mock(AuctionBidStreamPersistenceService.class),
                new AuctionBidStreamProperties(Duration.ofMillis(1), Duration.ofSeconds(1), 1, Duration.ofSeconds(1), 1),
                mock(AuctionBidStreamConsumerLeaderLock.class), mock(AuctionTimelineEventRepository.class), mapper);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> invoke(consumer, "serialize", Map.of("x", "y")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void worker의_runtime_예외는_잡아서_다시_루프를_시도한다() throws Exception {
        AuctionBidStreamPersistenceService persistence = mock(AuctionBidStreamPersistenceService.class);
        when(persistence.hasProjectionError()).thenThrow(new IllegalStateException("db down"));
        AuctionBidStreamConsumerLeaderLock lock = mock(AuctionBidStreamConsumerLeaderLock.class);
        when(lock.isLeader()).thenReturn(true);
        AuctionBidStreamConsumer consumer = new AuctionBidStreamConsumer(mock(StringRedisTemplate.class), persistence,
                new AuctionBidStreamProperties(Duration.ofMillis(1), Duration.ofSeconds(1), 1, Duration.ofSeconds(1), 1),
                lock, mock(AuctionTimelineEventRepository.class), new ObjectMapper());
        setField(consumer, "running", true);
        Thread worker = new Thread(() -> { try { invoke(consumer, "runWorker"); } catch (Exception ignored) { } });
        worker.start();
        Thread.sleep(30);
        setField(consumer, "running", false);
        worker.interrupt();
        worker.join(1000);
        org.assertj.core.api.Assertions.assertThat(worker.isAlive()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 새_stream의_정상_record도_수신후_ACK와_삭제한다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        AuctionBidStreamConsumerLeaderLock lock = mock(AuctionBidStreamConsumerLeaderLock.class);
        AuctionBidStreamPersistenceService persistence = mock(AuctionBidStreamPersistenceService.class);
        AuctionTimelineEvent inbox = mock(AuctionTimelineEvent.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        PendingMessages pending = mock(PendingMessages.class);
        when(streamOperations.pending(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L))).thenReturn(pending);
        when(pending.iterator()).thenReturn(java.util.Collections.emptyIterator());
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of("new-1"));
        when(record.getValue()).thenReturn(Map.of("eventType", "unknown"));
        when(persistence.recordMalformed("new-1", Map.of("eventType", "unknown"))).thenReturn(inbox);
        when(inbox.getStreamId()).thenReturn("new-1");
        when(persistence.hasProjectionError()).thenReturn(false);
        when(persistence.markError(org.mockito.ArgumentMatchers.eq("new-1"), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(streamOperations.<MapRecord<String, Object, Object>>read(org.mockito.ArgumentMatchers.any(org.springframework.data.redis.connection.stream.Consumer.class),
                org.mockito.ArgumentMatchers.any(org.springframework.data.redis.connection.stream.StreamReadOptions.class),
                org.mockito.ArgumentMatchers.<org.springframework.data.redis.connection.stream.StreamOffset<String>[]>any())).thenReturn(java.util.List.of(record));
        when(lock.isLeader()).thenReturn(true);
        AuctionBidStreamConsumer consumer = new AuctionBidStreamConsumer(redisTemplate, persistence,
                new AuctionBidStreamProperties(Duration.ofMillis(1), Duration.ofSeconds(1), 1, Duration.ofSeconds(1), 1),
                lock, mock(AuctionTimelineEventRepository.class), new ObjectMapper());
        setField(consumer, "running", true);

        invoke(consumer, "consumeUntilIdle");

        verify(streamOperations).acknowledge("event:timeline", "auction-timeline-persistence", RecordId.of("new-1"));
        verify(streamOperations).delete("event:timeline", RecordId.of("new-1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void worker는_pending이_없고_새_stream도_비어있으면_즉시_유휴상태로_돌아간다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        AuctionBidStreamPersistenceService persistence = mock(AuctionBidStreamPersistenceService.class);
        AuctionBidStreamConsumerLeaderLock lock = mock(AuctionBidStreamConsumerLeaderLock.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        PendingMessages pending = mock(PendingMessages.class);
        when(streamOperations.pending(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L))).thenReturn(pending);
        when(pending.iterator()).thenReturn(java.util.Collections.emptyIterator());
        when(persistence.hasProjectionError()).thenReturn(false);
        when(lock.isLeader()).thenReturn(true);
        AuctionBidStreamConsumer consumer = new AuctionBidStreamConsumer(redisTemplate, persistence,
                new AuctionBidStreamProperties(Duration.ofMillis(1), Duration.ofSeconds(1), 1, Duration.ofSeconds(1), 1),
                lock, mock(AuctionTimelineEventRepository.class), new ObjectMapper());
        setField(consumer, "running", true);

        invoke(consumer, "consumeUntilIdle");

        verify(streamOperations).read(org.mockito.ArgumentMatchers.any(org.springframework.data.redis.connection.stream.Consumer.class),
                org.mockito.ArgumentMatchers.any(org.springframework.data.redis.connection.stream.StreamReadOptions.class),
                org.mockito.ArgumentMatchers.<org.springframework.data.redis.connection.stream.StreamOffset<String>[]>any());
    }

    @Test
    void existing_Redis_group_오류만_무시한다() throws Exception {
        AuctionBidStreamConsumer consumer = consumer(mock(StringRedisTemplate.class), mock(AuctionBidStreamPersistenceService.class));

        org.assertj.core.api.Assertions.assertThat(invoke(consumer, "isExistingGroup",
                new org.springframework.dao.DataIntegrityViolationException("BUSYGROUP Consumer Group name already exists"))).isEqualTo(true);
        org.assertj.core.api.Assertions.assertThat(invoke(consumer, "isExistingGroup", new IllegalStateException("other"))).isEqualTo(false);
    }

    @Test
    void worker는_리더를_얻지_못한_상태에서_interrupt되면_종료한다() throws Exception {
        AuctionBidStreamConsumerLeaderLock lock = mock(AuctionBidStreamConsumerLeaderLock.class);
        when(lock.isLeader()).thenReturn(false);
        when(lock.tryAcquire()).thenReturn(false);
        AuctionBidStreamConsumer consumer = consumer(mock(StringRedisTemplate.class), mock(AuctionBidStreamPersistenceService.class));
        setField(consumer, "running", true);
        setField(consumer, "leaderLock", lock);
        Thread worker = new Thread(() -> { try { invoke(consumer, "runWorker"); } catch (Exception ignored) { } });
        worker.start();
        Thread.sleep(30);
        worker.interrupt();
        worker.join(1000);
        org.assertj.core.api.Assertions.assertThat(worker.isAlive()).isFalse();
    }

    @Test
    void inbox_projection_실패는_error로_기록한다() throws Exception {
        AuctionBidStreamPersistenceService persistence = mock(AuctionBidStreamPersistenceService.class);
        AuctionTimelineEventRepository inbox = mock(AuctionTimelineEventRepository.class);
        AuctionTimelineEvent pending = new AuctionTimelineEvent("inbox-fail", null, null, "wallet.charged.v1", 2,
                "{\"schemaVersion\":\"2\",\"eventId\":\"8ef477e7-1c80-42ea-a7af-8d8ea9c6d411\",\"eventType\":\"wallet.charged.v1\",\"userId\":\"1\",\"walletVersion\":\"2\",\"availableBalance\":\"10000\",\"frozenBalance\":\"0\",\"occurredAt\":\"2026-08-10T12:00:00Z\"}", Instant.now(), Instant.now());
        AuctionBidStreamConsumer consumer = new AuctionBidStreamConsumer(mock(StringRedisTemplate.class), persistence,
                new AuctionBidStreamProperties(Duration.ofMillis(1), Duration.ofSeconds(1), 1, Duration.ofSeconds(1), 1),
                mock(AuctionBidStreamConsumerLeaderLock.class), inbox, new ObjectMapper());
        when(persistence.hasProjectionError()).thenReturn(false);
        when(inbox.findFirstByProjectionStatusOrderByIdAsc(com.dbidding.auction.domain.AuctionBidEventProjectionStatus.PENDING)).thenReturn(java.util.Optional.of(pending));
        RuntimeException failure = new IllegalStateException("projection failed");
        doThrow(failure).when(persistence).project(org.mockito.ArgumentMatchers.any(WalletStateChangedStreamEvent.class));
        when(persistence.markError("inbox-fail", failure)).thenReturn(true);

        org.assertj.core.api.Assertions.assertThat(invoke(consumer, "projectOldestPending")).isEqualTo(true);
        verify(persistence).markError("inbox-fail", failure);
    }

    private AuctionBidStreamConsumer consumer(StringRedisTemplate redisTemplate, AuctionBidStreamPersistenceService persistence) {
        return new AuctionBidStreamConsumer(redisTemplate, persistence,
                new AuctionBidStreamProperties(Duration.ofMillis(1), Duration.ofSeconds(1), 2, Duration.ofSeconds(1), 10),
                mock(AuctionBidStreamConsumerLeaderLock.class), mock(AuctionTimelineEventRepository.class), new ObjectMapper());
    }

    private WalletStateChangedStreamEvent walletEvent(String streamId) {
        return new WalletStateChangedStreamEvent(streamId, java.util.UUID.randomUUID(), "wallet.charged.v1", 1, 1L,
                10_000L, 0L, null, null, null, com.dbidding.wallet.domain.PointTransactionType.CHARGE, 10_000L,
                "key", Instant.parse("2026-08-10T12:00:00Z"));
    }

    private Object invoke(Object target, String name, Object argument) throws Exception {
        java.lang.reflect.Method method = java.util.Arrays.stream(target.getClass().getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name) && candidate.getParameterCount() == 1).findFirst().orElseThrow();
        method.setAccessible(true);
        try {
            return method.invoke(target, argument);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            throw (Exception) exception.getCause();
        }
    }

    private Object invoke(Object target, String name, Throwable argument) throws Exception {
        java.lang.reflect.Method method = target.getClass().getDeclaredMethod(name, Throwable.class);
        method.setAccessible(true);
        return method.invoke(target, argument);
    }

    private Object invoke(Object target, String name) throws Exception {
        java.lang.reflect.Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        try {
            return method.invoke(target);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            throw (Exception) exception.getCause();
        }
    }

    private void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
