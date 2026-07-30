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
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AuctionSseConnectionManagerTest {
    @Test
    void 연결한_emitter에_경매_갱신_이벤트를_전송한다() throws Exception {
        AuctionSseConnectionManager manager = new AuctionSseConnectionManager();
        SseEmitter emitter = mock(SseEmitter.class);
        manager.register(emitter);

        manager.broadcast(event());

        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(manager.connectionCount()).isEqualTo(1);
    }

    @Test
    void 전송에_실패한_emitter는_연결_목록에서_제거한다() throws Exception {
        AuctionSseConnectionManager manager = new AuctionSseConnectionManager();
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
        LocalDateTime now = LocalDateTime.of(2026, 7, 30, 12, 0);
        return new BidPlacedPayload(
                null,
                1, 4, null,
                40_000L, 41_000L, 1_000L, 1, now.plusHours(1),
                AuctionPayloadStatus.OPEN, 2L, now
        );
    }
}
