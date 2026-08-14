package com.dbidding.sse.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class VirtualThreadSseTaskExecutorTest {

    @Test
    void 정상_실행시_submitted_active_completed_duration을_기록한다() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        VirtualThreadSseTaskExecutor executor = new VirtualThreadSseTaskExecutor("test-", registry, "auction-sse");
        CountDownLatch done = new CountDownLatch(1);

        executor.execute(() -> {
            try {
                Thread.sleep(1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            done.countDown();
        });

        assertThat(done.await(1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50); // finally 블록이 completed/duration을 기록할 시간을 준다

        assertThat(registry.get("dbidding.sse.executor.submitted")
                .tag("executor", "auction-sse").tag("thread_type", "virtual").counter().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.sse.executor.completed")
                .tag("executor", "auction-sse").tag("thread_type", "virtual").counter().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.sse.executor.active")
                .tag("executor", "auction-sse").tag("thread_type", "virtual").gauge().value()).isZero();
        assertThat(registry.get("dbidding.sse.executor.task.duration")
                .tag("executor", "auction-sse").tag("thread_type", "virtual").timer().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.sse.executor.failures")
                .tag("executor", "auction-sse").tag("thread_type", "virtual").counter().count()).isZero();
    }

    @Test
    void task가_예외를_던지면_failures를_기록하고_completed는_증가하지_않는다() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        VirtualThreadSseTaskExecutor executor = new VirtualThreadSseTaskExecutor("test-", registry, "notification-sse");
        CountDownLatch done = new CountDownLatch(1);

        executor.execute(() -> {
            try {
                throw new RuntimeException("boom");
            } finally {
                done.countDown();
            }
        });

        assertThat(done.await(1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50);

        assertThat(registry.get("dbidding.sse.executor.failures")
                .tag("executor", "notification-sse").tag("thread_type", "virtual").counter().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.sse.executor.completed")
                .tag("executor", "notification-sse").tag("thread_type", "virtual").counter().count()).isZero();
    }
}
