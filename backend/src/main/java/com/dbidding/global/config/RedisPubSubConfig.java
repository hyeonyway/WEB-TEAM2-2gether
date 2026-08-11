package com.dbidding.global.config;

import com.dbidding.auction.sse.AuctionStreamPublisher;
import com.dbidding.auction.sse.AuctionStreamRedisSubscriber;
import com.dbidding.notification.sse.NotificationPushPublisher;
import com.dbidding.notification.sse.NotificationPushRedisSubscriber;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 이 코드베이스 최초의 Redis pub/sub 설정(#281). Spring Boot 4의 기본 {@code ObjectMapper}
 * 자동구성이 (Jackson 3 계열 {@code tools.jackson}을 기본으로 노출해서) 기존 코드가 쓰는
 * Jackson 2 계열({@code com.fasterxml.jackson}) {@code ObjectMapper} 빈을 제공하지 않아서,
 * 여기서 직접 만들어 쓴다. {@code local-sse} 프로필에서는 Redis 연결을 전혀 시도하지 않도록
 * 이 설정 전체를 비활성화한다(#346).
 */
@Configuration
@Profile("!local-sse")
public class RedisPubSubConfig {

    @Bean
    public JsonMapper redisObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            AuctionStreamRedisSubscriber auctionStreamSubscriber,
            NotificationPushRedisSubscriber notificationPushSubscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(auctionStreamSubscriber, new ChannelTopic(AuctionStreamPublisher.CHANNEL));
        container.addMessageListener(notificationPushSubscriber, new ChannelTopic(NotificationPushPublisher.CHANNEL));
        return container;
    }
}
