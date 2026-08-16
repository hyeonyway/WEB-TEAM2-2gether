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

    private Object invoke(Object target, String name) throws Exception {
        java.lang.reflect.Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        try {
            return method.invoke(target);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            throw (Exception) exception.getCause();
        }
    }
}
