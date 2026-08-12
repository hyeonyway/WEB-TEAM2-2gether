package com.dbidding.auction.stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Duration;
import com.dbidding.auction.domain.AuctionBidEventInbox;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class AuctionBidStreamConsumerTest {
    @Test
    @SuppressWarnings("unchecked")
    void 처리_완료_이벤트는_ACK만_하고_재구성용_Stream에서는_삭제하지_않는다() {
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
                mock(AuctionBidStreamConsumerLeaderLock.class)
        );

        consumer.acknowledge(record);

        verify(streamOperations).acknowledge("auction:timeline-events", "auction-timeline-persistence", recordId);
        verifyNoMoreInteractions(streamOperations);
    }
}
