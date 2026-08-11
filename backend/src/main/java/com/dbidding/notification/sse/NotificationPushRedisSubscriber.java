package com.dbidding.notification.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Redis 채널을 구독해 역직렬화한 뒤 그대로 로컬 커넥션에 push한다 — 이미 완성된 payload를
 * 전달만 하므로 DB 조회 없음. subscriber(로컬 fan-out) 전용 {@code notificationFanOutTaskExecutor}를
 * 쓴다 — origin(저장+발행)용 {@code notificationTaskExecutor}와는 분리(#305). {@code local-sse}
 * 프로필에서는 Redis를 안 쓰므로 비활성화. 유저별 push를 동기로 할지 개별 task로 세분화할지는
 * {@link NotificationPushDispatcher}가 프로필({@code sse-virtual-threads})에 따라 결정한다(#362).
 */
@Component
@Profile("!local-sse")
@RequiredArgsConstructor
@Slf4j
public class NotificationPushRedisSubscriber implements MessageListener {
    private final NotificationPushDispatcher pushDispatcher;
    private final ObjectMapper objectMapper;

    @Async("notificationFanOutTaskExecutor")
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            List<NotificationPushMessage> parsed = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), new TypeReference<List<NotificationPushMessage>>() {
                    });
            parsed.forEach(entry -> pushDispatcher.dispatch(entry.userId(), entry.payload()));
        } catch (JsonProcessingException exception) {
            log.warn("event=notification.sse.redis_subscriber.deserialize_failed", exception);
        }
    }
}
