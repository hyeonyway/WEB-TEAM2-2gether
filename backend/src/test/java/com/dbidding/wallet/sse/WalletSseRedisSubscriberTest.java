package com.dbidding.wallet.sse;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

class WalletSseRedisSubscriberTest {
    @Test
    void 로컬_SSE_전달_실패는_Redis_리스너까지_전파하지_않는다() throws Exception {
        WalletSseConnectionManager connections = org.mockito.Mockito.mock(WalletSseConnectionManager.class);
        doThrow(new IllegalStateException("serialize failed")).when(connections)
                .push(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
        JsonMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        WalletSseRedisSubscriber subscriber = new WalletSseRedisSubscriber(connections, objectMapper);
        Message message = org.mockito.Mockito.mock(Message.class);
        org.mockito.Mockito.when(message.getBody()).thenReturn(objectMapper.writeValueAsBytes(new WalletBalanceChangedEvent(
                1, new com.dbidding.wallet.dto.WalletBalanceResponse(10_000L, 1_000L, 9_000L, 3L), 3L, Instant.now())));

        assertThatCode(() -> subscriber.onMessage(message, new byte[0])).doesNotThrowAnyException();
    }
}
