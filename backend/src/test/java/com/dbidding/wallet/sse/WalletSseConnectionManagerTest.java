package com.dbidding.wallet.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dbidding.global.security.session.SessionSseConnectionRegistry;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class WalletSseConnectionManagerTest {

    @Test
    void 지갑_이벤트는_같은_사용자의_연결에만_전송한다() throws Exception {
        WalletSseConnectionManager manager = new WalletSseConnectionManager(
                objectMapper(), new SyncTaskExecutor(), metrics());
        SseEmitter owner = mock(SseEmitter.class);
        SseEmitter otherUser = mock(SseEmitter.class);
        manager.register(1, owner);
        manager.register(2, otherUser);

        manager.push(1, payload(10));

        verify(owner, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(otherUser, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(manager.connectionCount(1)).isEqualTo(1);
    }

    @Test
    void 지갑_이벤트_전송은_전용_executor에_위임한다() {
        TaskExecutor executor = mock(TaskExecutor.class);
        WalletSseConnectionManager manager = new WalletSseConnectionManager(objectMapper(), executor, metrics());
        manager.register(1, mock(SseEmitter.class));

        manager.push(1, payload(10));

        verify(executor).execute(any(Runnable.class));
    }

    @Test
    void 세션_종료_시_해당_세션의_지갑_SSE_연결도_종료한다() {
        SessionSseConnectionRegistry registry = new SessionSseConnectionRegistry();
        WalletSseConnectionManager manager = new WalletSseConnectionManager(
                registry, objectMapper(), new SyncTaskExecutor(), metrics());
        SseEmitter emitter = mock(SseEmitter.class);

        manager.register(1, "session-a", emitter);
        registry.disconnect("session-a");

        verify(emitter).complete();
    }

    @Test
    void 연결_등록과_해제에_따라_지갑_SSE_연결_Gauge가_변한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WalletSseConnectionManager manager = new WalletSseConnectionManager(
                objectMapper(), new SyncTaskExecutor(), new WalletSseMetrics(registry));
        SseEmitter emitter = mock(SseEmitter.class);
        final Runnable[] onCompletion = new Runnable[1];
        org.mockito.Mockito.doAnswer(invocation -> {
            onCompletion[0] = invocation.getArgument(0);
            return null;
        }).when(emitter).onCompletion(any(Runnable.class));

        manager.register(1, emitter);

        assertThat(registry.get("dbidding.sse.connections").tag("stream", "wallet").gauge().value()).isEqualTo(1);
        onCompletion[0].run();
        assertThat(registry.get("dbidding.sse.connections").tag("stream", "wallet").gauge().value()).isZero();
        assertThat(registry.get("dbidding.sse.connections.closed")
                .tag("stream", "wallet").tag("reason", "completion").counter().count()).isEqualTo(1);
    }

    @Test
    void 전송_실패시_send_failure_원인으로_한번만_기록한다() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WalletSseConnectionManager manager = new WalletSseConnectionManager(
                objectMapper(), new SyncTaskExecutor(), new WalletSseMetrics(registry));
        SseEmitter emitter = mock(SseEmitter.class);
        final Runnable[] onCompletion = new Runnable[1];
        org.mockito.Mockito.doAnswer(invocation -> {
            onCompletion[0] = invocation.getArgument(0);
            return null;
        }).when(emitter).onCompletion(any(Runnable.class));
        org.mockito.Mockito.doThrow(new IOException("disconnected"))
                .when(emitter).send(ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));

        manager.register(1, emitter);
        onCompletion[0].run();

        assertThat(registry.get("dbidding.wallet.sse.send.failures").counter().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.sse.connections.closed")
                .tag("stream", "wallet").tag("reason", "send_failure").counter().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.sse.connections.closed")
                .tag("stream", "wallet").tag("reason", "completion").counter().count()).isZero();
    }

    private WalletSseMetrics metrics() {
        return new WalletSseMetrics(new SimpleMeterRegistry());
    }

    private WalletSsePayload payload(long version) {
        return WalletSsePayload.from(new WalletBalanceChangedEvent(
                1, new WalletBalanceResponse(10_000L, 1_000L, 9_000L), version, Instant.parse("2026-08-12T00:00:00Z")
        ));
    }

    private ObjectMapper objectMapper() {
        return JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    }
}
