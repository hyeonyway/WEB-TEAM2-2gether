package com.dbidding.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class NotificationPushDispatcherProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withBean(NotificationSseConnectionManager.class, () -> mock(NotificationSseConnectionManager.class));

    @Test
    void 기본_프로필에서는_SynchronousNotificationPushDispatcher만_등록된다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(NotificationPushDispatcher.class);
            assertThat(context).hasSingleBean(SynchronousNotificationPushDispatcher.class);
            assertThat(context).doesNotHaveBean(VirtualThreadNotificationPushDispatcher.class);
        });
    }

    @Test
    void sse_virtual_threads_프로필에서는_VirtualThreadNotificationPushDispatcher만_등록된다() {
        contextRunner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("sse-virtual-threads"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(NotificationPushDispatcher.class);
                    assertThat(context).hasSingleBean(VirtualThreadNotificationPushDispatcher.class);
                    assertThat(context).doesNotHaveBean(SynchronousNotificationPushDispatcher.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({SynchronousNotificationPushDispatcher.class, VirtualThreadNotificationPushDispatcher.class})
    static class TestConfiguration {
    }
}
