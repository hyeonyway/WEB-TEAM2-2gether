package com.dbidding.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
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

    @Test
    void fanOut_executor가_포화되면_호출_스레드에서_작업을_실행한다() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationExecutorConfig config = new NotificationExecutorConfig(registry);
        ReflectionTestUtils.setField(config, "fanOutCorePoolSize", 1);
        ReflectionTestUtils.setField(config, "fanOutMaxPoolSize", 1);
        ReflectionTestUtils.setField(config, "fanOutQueueCapacity", 1);
        // !sse-virtual-threads 프로필용 빈(#362) — bounded pool 포화 동작을 검증한다.
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.notificationFanOutTaskExecutor();
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
        assertThat(registry.get("dbidding.sse.broadcast.saturated").tag("executor", "notification-fanout").counter().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.sse.broadcast.saturated.caller-runs.duration")
                .tag("executor", "notification-fanout").timer().count()).isEqualTo(1);
        release.countDown();
        executor.shutdown();
    }

    @Test
    void origin과_fanOut_executor는_서로_다른_인스턴스다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationExecutorConfig config = new NotificationExecutorConfig(registry);
        ReflectionTestUtils.setField(config, "corePoolSize", 1);
        ReflectionTestUtils.setField(config, "maxPoolSize", 1);
        ReflectionTestUtils.setField(config, "queueCapacity", 1);
        ReflectionTestUtils.setField(config, "fanOutCorePoolSize", 1);
        ReflectionTestUtils.setField(config, "fanOutMaxPoolSize", 1);
        ReflectionTestUtils.setField(config, "fanOutQueueCapacity", 1);

        ThreadPoolTaskExecutor origin = config.notificationTaskExecutor();
        ThreadPoolTaskExecutor fanOut = (ThreadPoolTaskExecutor) config.notificationFanOutTaskExecutor();

        assertThat(origin).isNotSameAs(fanOut);
        assertThat(origin.getThreadNamePrefix()).isEqualTo("notification-");
        assertThat(fanOut.getThreadNamePrefix()).isEqualTo("notification-fanout-");
        origin.shutdown();
        fanOut.shutdown();
    }

    @Test
    void sse_virtual_threads_프로필의_fanOut_executor는_가상_스레드에서_실행한다() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationExecutorConfig config = new NotificationExecutorConfig(registry);
        TaskExecutor executor = config.notificationFanOutVirtualTaskExecutor();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Boolean> isVirtual = new AtomicReference<>();
        AtomicReference<String> threadName = new AtomicReference<>();

        executor.execute(() -> {
            isVirtual.set(Thread.currentThread().isVirtual());
            threadName.set(Thread.currentThread().getName());
            done.countDown();
        });

        assertThat(done.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(isVirtual).hasValue(true);
        assertThat(threadName.get()).startsWith("notification-fanout-");
    }
}
