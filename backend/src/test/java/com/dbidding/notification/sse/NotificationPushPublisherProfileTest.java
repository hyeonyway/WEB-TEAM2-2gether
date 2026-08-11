package com.dbidding.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

class NotificationPushPublisherProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withBean(NotificationSseConnectionManager.class, () -> mock(NotificationSseConnectionManager.class))
            .withBean(NotificationPushDispatcher.class, () -> mock(NotificationPushDispatcher.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void 기본_프로필에서는_RedisNotificationPushPublisher만_등록된다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(NotificationPushPublisher.class);
            assertThat(context).hasSingleBean(RedisNotificationPushPublisher.class);
            assertThat(context).doesNotHaveBean(LocalNotificationPushPublisher.class);
        });
    }

    @Test
    void local_sse_프로필에서는_LocalNotificationPushPublisher만_등록된다() {
        contextRunner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local-sse"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(NotificationPushPublisher.class);
                    assertThat(context).hasSingleBean(LocalNotificationPushPublisher.class);
                    assertThat(context).doesNotHaveBean(RedisNotificationPushPublisher.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({LocalNotificationPushPublisher.class, RedisNotificationPushPublisher.class})
    static class TestConfiguration {
    }
}
