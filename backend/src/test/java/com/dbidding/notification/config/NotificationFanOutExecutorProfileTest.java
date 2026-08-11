package com.dbidding.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class NotificationFanOutExecutorProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withUserConfiguration(NotificationExecutorConfig.class);

    @Test
    void 기본_프로필에서는_bounded_ThreadPoolTaskExecutor를_쓴다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("notificationFanOutTaskExecutor")).isInstanceOf(ThreadPoolTaskExecutor.class);
        });
    }

    @Test
    void sse_virtual_threads_프로필에서는_가상_스레드_executor를_쓴다() {
        contextRunner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("sse-virtual-threads"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean("notificationFanOutTaskExecutor")).isInstanceOf(SimpleAsyncTaskExecutor.class);
                });
    }
}
