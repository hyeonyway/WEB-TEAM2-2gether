package com.dbidding.wallet.sse;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class WalletSseExecutorConfigTest {

    @Test
    void executor가_포화되면_호출_스레드를_막지_않고_작업을_버린다() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WalletSseExecutorConfig config = new WalletSseExecutorConfig(registry);
        ReflectionTestUtils.setField(config, "corePoolSize", 1);
        ReflectionTestUtils.setField(config, "maxPoolSize", 1);
        ReflectionTestUtils.setField(config, "queueCapacity", 1);
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.walletSseTaskExecutor();
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

        assertThat(executionThread).hasNullValue();
        assertThat(registry.get("dbidding.sse.broadcast.saturated").tag("executor", "wallet").counter().count()).isEqualTo(1);
        release.countDown();
        executor.shutdown();
    }
}
