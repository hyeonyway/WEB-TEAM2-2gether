package com.dbidding.wallet.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class WalletSseConnectionManagerTest {

    @Test
    void 지갑_이벤트는_같은_사용자의_연결에만_전송한다() throws Exception {
        WalletSseConnectionManager manager = new WalletSseConnectionManager(objectMapper(), new SyncTaskExecutor());
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
        WalletSseConnectionManager manager = new WalletSseConnectionManager(objectMapper(), executor);
        manager.register(1, mock(SseEmitter.class));

        manager.push(1, payload(10));

        verify(executor).execute(any(Runnable.class));
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
