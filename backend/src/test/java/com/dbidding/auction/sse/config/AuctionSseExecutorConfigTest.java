package com.dbidding.auction.sse.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class AuctionSseExecutorConfigTest {

    @Test
    void executor가_포화되면_호출_스레드에서_작업을_실행한다() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuctionSseExecutorConfig config = new AuctionSseExecutorConfig(registry);
        ReflectionTestUtils.setField(config, "corePoolSize", 1);
        ReflectionTestUtils.setField(config, "maxPoolSize", 1);
        ReflectionTestUtils.setField(config, "queueCapacity", 1);
        // !sse-virtual-threads 프로필용 빈(#362) — bounded pool 포화 동작을 검증한다.
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.auctionSseTaskExecutor();
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
        assertThat(registry.get("dbidding.sse.broadcast.saturated").tag("executor", "auction").counter().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.sse.broadcast.saturated.caller-runs.duration")
                .tag("executor", "auction").timer().count()).isEqualTo(1);
        release.countDown();
        executor.shutdown();
    }

    @Test
    void sse_virtual_threads_프로필의_executor는_가상_스레드에서_실행한다() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuctionSseExecutorConfig config = new AuctionSseExecutorConfig(registry);
        TaskExecutor executor = config.auctionSseVirtualTaskExecutor();
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
        assertThat(threadName.get()).startsWith("auction-sse-");
    }
}
