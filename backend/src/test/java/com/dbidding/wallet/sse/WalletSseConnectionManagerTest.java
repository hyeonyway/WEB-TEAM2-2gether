package com.dbidding.wallet.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dbidding.global.security.session.MeSseConnectionManager;
import com.dbidding.sse.metrics.SseMetrics;
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
        MeSseConnectionManager connectionManager = meSseConnectionManager(new SyncTaskExecutor());
        WalletSseConnectionManager manager = manager(connectionManager, new SyncTaskExecutor());
        SseEmitter owner = mock(SseEmitter.class);
        SseEmitter otherUser = mock(SseEmitter.class);
        connectionManager.register(1, owner);
        connectionManager.register(2, otherUser);

        manager.push(1, payload(10));

        verify(owner, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(otherUser, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(manager.connectionCount(1)).isEqualTo(1);
    }

    @Test
    void 지갑_이벤트_전송은_전용_executor에_위임한다() {
        TaskExecutor executor = mock(TaskExecutor.class);
        MeSseConnectionManager connectionManager = meSseConnectionManager(new SyncTaskExecutor());
        WalletSseConnectionManager manager = manager(connectionManager, executor);
        connectionManager.register(1, mock(SseEmitter.class));

        manager.push(1, payload(10));

        verify(executor).execute(any(Runnable.class));
    }

    @Test
    void 전송_실패시_send_failure_원인으로_한번만_기록한다() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MeSseConnectionManager connectionManager = meSseConnectionManager(new SyncTaskExecutor());
        WalletSseConnectionManager manager = new WalletSseConnectionManager(
                connectionManager, objectMapper(), new SyncTaskExecutor(), new SseMetrics(meterRegistry, "wallet"));
        SseEmitter emitter = mock(SseEmitter.class);
        connectionManager.register(1, emitter);
        org.mockito.Mockito.doThrow(new IOException("disconnected"))
                .when(emitter).send(ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));

        manager.push(1, payload(10));

        assertThat(meterRegistry.get("dbidding.wallet.sse.send.failures").counter().count()).isEqualTo(1);
    }

    private WalletSseConnectionManager manager(MeSseConnectionManager connectionManager, TaskExecutor executor) {
        return new WalletSseConnectionManager(connectionManager, objectMapper(), executor, metrics());
    }

    private MeSseConnectionManager meSseConnectionManager(TaskExecutor heartbeatExecutor) {
        return new MeSseConnectionManager(new SseMetrics(new SimpleMeterRegistry(), "me"), heartbeatExecutor);
    }

    private SseMetrics metrics() {
        return new SseMetrics(new SimpleMeterRegistry(), "wallet");
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
