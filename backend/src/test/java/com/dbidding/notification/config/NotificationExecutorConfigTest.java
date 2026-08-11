package com.dbidding.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationExecutorConfigTest {

    @Test
    void executor가_포화되면_호출_스레드에서_작업을_실행한다() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationExecutorConfig config = new NotificationExecutorConfig(registry);
        ReflectionTestUtils.setField(config, "corePoolSize", 1);
        ReflectionTestUtils.setField(config, "maxPoolSize", 1);
        ReflectionTestUtils.setField(config, "queueCapacity", 1);
        ThreadPoolTaskExecutor executor = config.notificationTaskExecutor();
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> executionThread = new AtomicReference<>();

        executor.execute(() -> {
            running.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(running.await(1, TimeUnit.SECONDS)).isTrue();
        executor.execute(() -> { });
        executor.execute(() -> executionThread.set(Thread.currentThread().getName()));

        assertThat(executionThread).hasValue(Thread.currentThread().getName());
        assertThat(registry.get("dbidding.sse.broadcast.saturated").tag("executor", "notification").counter().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.sse.broadcast.saturated.caller-runs.duration")
                .tag("executor", "notification").timer().count()).isEqualTo(1);
        release.countDown();
        executor.shutdown();
    }
}
