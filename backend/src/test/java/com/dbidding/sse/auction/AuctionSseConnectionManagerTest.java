package com.dbidding.sse.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dbidding.sse.auction.payload.AuctionPayload;
import com.dbidding.sse.auction.payload.AuctionPayloadStatus;
import com.dbidding.sse.auction.payload.BidPlacedPayload;
import java.io.IOException;
import java.time.Instant;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AuctionSseConnectionManagerTest {
    @Test
    void 연결한_emitter에_경매_갱신_이벤트를_전송한다() throws Exception {
        AuctionSseConnectionManager manager = new AuctionSseConnectionManager(Runnable::run);
        SseEmitter emitter = mock(SseEmitter.class);
        manager.register(emitter);

        manager.broadcast(event());

        ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter, times(2)).send(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues().get(1).build())
                .extracting(data -> data.getData().toString())
                .anySatisfy(data -> assertThat(data).startsWith("event:BID_PLACED\n"));
        assertThat(manager.connectionCount()).isEqualTo(1);
    }

    @Test
    void 전송에_실패한_emitter는_연결_목록에서_제거한다() throws Exception {
        AuctionSseConnectionManager manager = new AuctionSseConnectionManager(Runnable::run);
        SseEmitter emitter = mock(SseEmitter.class);
        manager.register(emitter);
        doThrow(new IOException("disconnected"))
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        manager.broadcast(event());

        assertThat(manager.connectionCount()).isZero();
        verify(emitter).complete();
    }

    private AuctionPayload event() {
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        return new BidPlacedPayload(
                null,
                1, 4, null,
                40_000L, 41_000L, 1_000L, 1, now.plusSeconds(3600),
                AuctionPayloadStatus.OPEN, 2L, now
        );
    }
}
