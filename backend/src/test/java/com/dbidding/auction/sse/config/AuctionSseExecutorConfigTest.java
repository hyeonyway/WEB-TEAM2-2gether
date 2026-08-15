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

    @Test
    void 기본_프로필에서도_heartbeat용_executor가_포화돼도_broadcast용_executor는_영향받지_않는다() throws InterruptedException {
        // #507: 기본 프로필에서 send는 broadcast() 호출 스레드에서 바로 실행되므로, 느린
        // 클라이언트 하나가 broadcast용 pool을 막을 수는 있어도 heartbeat용 pool과는 무관해야
        // 하고, 그 역(heartbeat용 pool 포화가 broadcast를 막는 것)도 없어야 한다.
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuctionSseExecutorConfig config = new AuctionSseExecutorConfig(registry);
        ReflectionTestUtils.setField(config, "corePoolSize", 1);
        ReflectionTestUtils.setField(config, "maxPoolSize", 1);
        ReflectionTestUtils.setField(config, "queueCapacity", 0);
        ReflectionTestUtils.setField(config, "broadcastCorePoolSize", 1);
        ReflectionTestUtils.setField(config, "broadcastMaxPoolSize", 1);
        ReflectionTestUtils.setField(config, "broadcastQueueCapacity", 0);
        ThreadPoolTaskExecutor heartbeatExecutor = (ThreadPoolTaskExecutor) config.auctionSseTaskExecutor();
        ThreadPoolTaskExecutor broadcastExecutor = (ThreadPoolTaskExecutor) config.auctionSseBroadcastTaskExecutor();

        CountDownLatch heartbeatRunning = new CountDownLatch(1);
        CountDownLatch releaseHeartbeat = new CountDownLatch(1);
        heartbeatExecutor.execute(() -> {
            heartbeatRunning.countDown();
            try {
                releaseHeartbeat.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(heartbeatRunning.await(1, TimeUnit.SECONDS)).isTrue();

        CountDownLatch broadcastDone = new CountDownLatch(1);
        AtomicReference<String> broadcastThread = new AtomicReference<>();
        broadcastExecutor.execute(() -> {
            broadcastThread.set(Thread.currentThread().getName());
            broadcastDone.countDown();
        });

        assertThat(broadcastDone.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(broadcastThread.get()).startsWith("auction-sse-broadcast-");
        releaseHeartbeat.countDown();
        heartbeatExecutor.shutdown();
        broadcastExecutor.shutdown();
    }

    @Test
    void broadcast_executor는_send_executor의_캡이_꽉_차있어도_즉시_task를_받는다() throws InterruptedException {
        // #507: send용 executor(auctionSseTaskExecutor)를 캡 1로 포화시켜 놓아도,
        // 분리된 broadcast용 executor(캡 없음)는 그 세마포어와 무관해 곧바로 task를 실행해야 한다.
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuctionSseExecutorConfig config = new AuctionSseExecutorConfig(registry);
        ReflectionTestUtils.setField(config, "virtualMaxConcurrency", 1);
        TaskExecutor sendExecutor = config.auctionSseVirtualTaskExecutor();
        TaskExecutor broadcastExecutor = config.auctionSseBroadcastVirtualTaskExecutor();

        CountDownLatch sendRunning = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        sendExecutor.execute(() -> {
            sendRunning.countDown();
            try {
                releaseSend.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(sendRunning.await(1, TimeUnit.SECONDS)).isTrue();

        CountDownLatch broadcastDone = new CountDownLatch(1);
        broadcastExecutor.execute(broadcastDone::countDown);

        assertThat(broadcastDone.await(1, TimeUnit.SECONDS)).isTrue();
        releaseSend.countDown();
    }
}
