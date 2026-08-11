package com.dbidding.auction.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.event.BidPlacedEvent;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

class AuctionStreamRedisSubscriberTest {

    @Test
    void 수신한_메시지를_역직렬화해서_type을_복원한_뒤_그대로_broadcast한다() throws Exception {
        AuctionSseConnectionManager connectionManager = mock(AuctionSseConnectionManager.class);
        JsonMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        AuctionStreamRedisSubscriber subscriber = new AuctionStreamRedisSubscriber(connectionManager, objectMapper);
        BidPlacedEvent event = new BidPlacedEvent(
                10, 1, 7, 5, 20L, 40_000L, 50_000L, 1_000L, 2,
                Instant.parse("2026-08-10T01:00:00Z"), AuctionStatus.OPEN, Instant.parse("2026-08-10T00:00:00Z"));
        AuctionStreamPayload payload = AuctionStreamPayload.bidPlaced(event);
        byte[] body = objectMapper.writeValueAsBytes(new AuctionStreamMessage(payload.type(), payload));
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);

        subscriber.onMessage(message, null);

        verify(connectionManager).broadcast(argThat((AuctionStreamPayload received) ->
                received.type() == AuctionStreamEventType.BID_PLACED
                        && received.auctionId().equals(10)
                        && received.bidderId().equals(7)
        ));
    }
}
