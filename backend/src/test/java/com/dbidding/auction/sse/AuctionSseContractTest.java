package com.dbidding.auction.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AuctionSseContractTest {
    private final Instant now = Instant.parse("2026-07-30T12:00:00Z");

    @Test
    void 프론트_계약의_이벤트명과_snake_case_필드를_유지한다() throws Exception {
        var payload = bidPayload();
        var mapper = JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

        var json = mapper.readTree(mapper.writeValueAsBytes(payload));

        assertThat(json.has("type")).isFalse();
        assertThat(json.get("auction_id").asInt()).isEqualTo(10);
        assertThat(json.get("bidder_id").asInt()).isEqualTo(7);
        assertThat(json.get("current_price").asLong()).isEqualTo(50_000L);
        assertThat(json.has("auction_version")).isFalse();
    }

    @Test
    void 종료_이벤트는_현재가와_낙찰가를_서로_다른_필드로_직렬화한다() throws Exception {
        AuctionClosedEvent event = new AuctionClosedEvent(
                10, 1, "Pikachu", "10", "KO", "thumb", 7, 5,
                40_000L, 50_000L, 55_000L, 1_000L, 2,
                Instant.parse("2026-08-03T12:00:00Z"), AuctionStatus.ENDED,
                Instant.parse("2026-08-03T12:00:00Z"));
        var mapper = JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

        var json = mapper.readTree(mapper.writeValueAsBytes(AuctionStreamPayload.closed(event)));

        assertThat(json.has("current_price")).isTrue();
        assertThat(json.get("current_price").asLong()).isEqualTo(50_000L);
        assertThat(json.get("final_price").asLong()).isEqualTo(55_000L);
    }

    @Test
    void 전달_실패한_연결은_제거한다() throws Exception {
        var manager = new AuctionSseConnectionManager();
        SseEmitter emitter = mock(SseEmitter.class);
        manager.register(emitter);
        doThrow(new IOException("disconnected")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        manager.broadcast(bidPayload());

        assertThat(manager.connectionCount()).isZero();
        verify(emitter).complete();
    }

    @Test
    void heartbeat은_연결된_emitter에_주석_메시지를_전송한다() throws Exception {
        var manager = new AuctionSseConnectionManager();
        SseEmitter emitter = mock(SseEmitter.class);
        manager.register(emitter);
        SseEmitter.SseEventBuilder heartbeat = mock(SseEmitter.SseEventBuilder.class);
        when(heartbeat.comment("heartbeat")).thenReturn(heartbeat);

        try (MockedStatic<SseEmitter> sseEmitter = mockStatic(SseEmitter.class)) {
            sseEmitter.when(SseEmitter::event).thenReturn(heartbeat);

            manager.heartbeat();
        }

        verify(heartbeat).comment("heartbeat");
        verify(emitter).send(heartbeat);
    }

    @Test
    void 테스트용_연결_종료는_모든_emitter를_완료하고_제거한다() {
        var manager = new AuctionSseConnectionManager();
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        manager.register(first);
        manager.register(second);

        manager.disconnectAll();

        assertThat(manager.connectionCount()).isZero();
        verify(first).complete();
        verify(second).complete();
    }

    @Test
    void 커밋_후_전달_리스너는_도메인_이벤트를_SSE_payload로_변환하여_브로드캐스트한다() {
        AuctionSseConnectionManager manager = mock(AuctionSseConnectionManager.class);
        AuctionSseEventListener listener = new AuctionSseEventListener(manager);
        Instant occurredAt = Instant.parse("2026-08-03T11:00:00Z");
        BidPlacedEvent event = new BidPlacedEvent(
                10, 1, 7, 5, 20L, 40_000L, 50_000L, 1_000L, 2,
                occurredAt.plus(Duration.ofHours(1)), AuctionStatus.OPEN, occurredAt
        );

        listener.onBidPlaced(event);

        verify(manager).broadcast(AuctionStreamPayload.bidPlaced(event));
    }

    @Test
    void 테스트_이벤트_엔드포인트는_test_프로필에서만_활성화된다() {
        Profile profile = AuctionSseTestEventController.class.getAnnotation(Profile.class);

        assertThat(profile.value()).containsExactly("test");
    }

    @Test
    void 경매_SSE_전송과_heartbeat는_전용_executor에서_비동기로_실행된다() throws Exception {
        Async broadcast = AuctionSseConnectionManager.class
                .getMethod("broadcast", AuctionStreamPayload.class).getAnnotation(Async.class);
        Async heartbeat = AuctionSseConnectionManager.class.getMethod("heartbeat").getAnnotation(Async.class);

        assertThat(broadcast.value()).isEqualTo("auctionSseTaskExecutor");
        assertThat(heartbeat.value()).isEqualTo("auctionSseTaskExecutor");
    }

    @Test
    void 테스트_입찰_이벤트는_버전과_가격을_순차적으로_증가시킨다() {
        AuctionSseConnectionManager manager = mock(AuctionSseConnectionManager.class);
        AuctionSseTestAuctionReader reader = mock(AuctionSseTestAuctionReader.class);
        when(reader.findRandomActiveAuction()).thenReturn(Optional.of(new AuctionSseTestAuctionReader.Snapshot(
                10, 40_000L, 40_000L, 1_000L, 0,
                Instant.now().plus(Duration.ofHours(1)), "OPEN", 5)));
        AuctionSseTestBidApplicationService service =
                new AuctionSseTestBidApplicationService(manager, reader, Clock.systemUTC());

        AuctionStreamPayload first = service.publishRandomBid();
        AuctionStreamPayload second = service.publishRandomBid();

        assertThat(second.currentPrice()).isEqualTo(first.currentPrice() + 1_000L);
        verify(manager).broadcast(first);
        verify(manager).broadcast(second);
    }

    private AuctionStreamPayload bidPayload() {
        return payloadFor(10);
    }

    private AuctionStreamPayload payloadFor(int auctionId) {
        return new AuctionStreamPayload(
                AuctionStreamEventType.BID_PLACED, auctionId, null, null, null, null, null, null,
                7, 5, null, 40_000L, 50_000L, null, 1_000L, 2,
                now.plusSeconds(3600), AuctionStatus.OPEN, null, now
        );
    }
}
