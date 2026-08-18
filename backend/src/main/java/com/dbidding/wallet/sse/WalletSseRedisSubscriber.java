package com.dbidding.wallet.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletSseRedisSubscriber implements MessageListener {
    private final WalletSseConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            WalletBalanceChangedEvent event = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), WalletBalanceChangedEvent.class);
            connectionManager.push(event.userId(), WalletSsePayload.from(event));
        } catch (JsonProcessingException exception) {
            log.warn("event=wallet.sse.redis_subscriber.deserialize_failed", exception);
        } catch (RuntimeException exception) {
            log.warn("event=wallet.sse.redis_subscriber.delivery_failed", exception);
        }
    }
}
