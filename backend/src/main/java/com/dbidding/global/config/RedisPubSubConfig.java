package com.dbidding.global.config;

import com.dbidding.auction.sse.AuctionStreamPublisher;
import com.dbidding.auction.sse.AuctionStreamRedisSubscriber;
import com.dbidding.notification.sse.NotificationPushPublisher;
import com.dbidding.notification.sse.NotificationPushRedisSubscriber;
import com.dbidding.wallet.sse.WalletSsePublisher;
import com.dbidding.wallet.sse.WalletSseRedisSubscriber;
import com.dbidding.global.security.session.SessionSseTerminationPublisher;
import com.dbidding.global.security.session.SessionSseTerminationRedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Pub/Sub 리스너 설정이다. Jackson 2 mapper는 {@link Jackson2ObjectMapperConfig}에서
 * 제공한다.
 */
@Configuration
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            AuctionStreamRedisSubscriber auctionStreamSubscriber,
            NotificationPushRedisSubscriber notificationPushSubscriber,
            WalletSseRedisSubscriber walletSseRedisSubscriber,
            SessionSseTerminationRedisSubscriber sessionSseTerminationSubscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(auctionStreamSubscriber, new ChannelTopic(AuctionStreamPublisher.CHANNEL));
        container.addMessageListener(notificationPushSubscriber, new ChannelTopic(NotificationPushPublisher.CHANNEL));
        container.addMessageListener(walletSseRedisSubscriber, new ChannelTopic(WalletSsePublisher.CHANNEL));
        container.addMessageListener(sessionSseTerminationSubscriber, new ChannelTopic(SessionSseTerminationPublisher.CHANNEL));
        return container;
    }
}
