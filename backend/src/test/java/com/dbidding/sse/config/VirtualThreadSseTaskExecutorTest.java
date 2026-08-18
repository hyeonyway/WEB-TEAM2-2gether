package com.dbidding.sse.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test
    void maxConcurrency_설정시_동시_실행이_상한을_넘지_않는다() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        VirtualThreadSseTaskExecutor executor = new VirtualThreadSseTaskExecutor("test-", registry, "auction-sse", 1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        Thread.ofVirtual().start(() -> executor.execute(() -> {
            firstStarted.countDown();
            try {
                releaseFirst.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

        // #585: permit이 없으면 두 번째 task는 대기하지 않고 그 자리에서 discard된다
        // (예전엔 permit이 빌 때까지 기다렸다가 나중에 실행됐음 — 그 "대기" 자체가 힙에
        // 계속 쌓이는 가상스레드였다는 게 문제였다).
        executor.execute(secondStarted::countDown);

        assertThat(secondStarted.await(300, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(registry.get("dbidding.sse.executor.active")
                .tag("executor", "auction-sse").tag("thread_type", "virtual").gauge().value()).isEqualTo(1);
        assertThat(registry.get("dbidding.sse.broadcast.saturated")
                .tag("executor", "auction-sse").counter().count()).isEqualTo(1);

        releaseFirst.countDown();

        // permit이 풀려도 이미 discard된 두 번째 task는 재시도되지 않는다 — 영영 실행 안 됨.
        assertThat(secondStarted.await(300, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test
    void 캡이_꽉_찬_상태에서도_execute_호출자는_블로킹되지_않는다() throws InterruptedException {
        // #585: permit 획득 시도(tryAcquire)를 execute() 호출자 스레드에서 논블로킹으로
        // 하므로, 캡이 꽉 차 있어도 execute() 자체는 항상 즉시 리턴해야 한다 — 그래야
        // broadcast()의 emitter 순회 루프가 캡 때문에 안 막힌다는 #575의 목표가 유지된다.
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        VirtualThreadSseTaskExecutor executor = new VirtualThreadSseTaskExecutor("test-", registry, "auction-sse", 1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        executor.execute(() -> {
            firstStarted.countDown();
            try {
                releaseFirst.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

        long startNanos = System.nanoTime();
        executor.execute(() -> { });
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

        assertThat(elapsedMillis).isLessThan(300);

        releaseFirst.countDown();
    }

    @Test
    void 캡이_꽉_찬_상태에서_제출된_task는_가상스레드를_만들지_않고_버려진다() throws InterruptedException {
        // #585: 힙 OOM의 원인이 "permit을 기다리는 가상스레드가 힙에 계속 쌓이는 것"이었으므로,
        // discard되는 task는 애초에 super.execute()(가상스레드 생성) 자체를 안 타야 한다.
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        VirtualThreadSseTaskExecutor executor = new VirtualThreadSseTaskExecutor("test-", registry, "auction-sse", 1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean secondTaskRan = new AtomicBoolean(false);

        executor.execute(() -> {
            firstStarted.countDown();
            try {
                releaseFirst.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

        executor.execute(() -> secondTaskRan.set(true));

        assertThat(registry.get("dbidding.sse.executor.submitted")
                .tag("executor", "auction-sse").tag("thread_type", "virtual").counter().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.sse.broadcast.saturated")
                .tag("executor", "auction-sse").counter().count()).isEqualTo(1);

        releaseFirst.countDown();
        Thread.sleep(100);

        assertThat(secondTaskRan.get()).isFalse();
    }
}
