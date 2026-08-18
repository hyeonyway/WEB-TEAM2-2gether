package com.dbidding.sse.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
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

        Thread.ofVirtual().start(() -> executor.execute(secondStarted::countDown));

        // permit이 없어서 첫 번째가 끝날 때까지 두 번째는 실행 자체가 시작되지 않는다.
        assertThat(secondStarted.await(300, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(registry.get("dbidding.sse.executor.active")
                .tag("executor", "auction-sse").tag("thread_type", "virtual").gauge().value()).isEqualTo(1);

        releaseFirst.countDown();

        assertThat(secondStarted.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void 캡이_꽉_찬_상태에서도_execute_호출자는_블로킹되지_않는다() throws InterruptedException {
        // #575: acquire()가 execute() 호출자가 아니라 super.execute()가 띄우는 새 가상스레드
        // 안에서 블로킹해야 한다 — 그래야 broadcast()의 emitter 순회 루프가 캡 때문에 안 막힌다.
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

        // permit이 하나도 없는 상태에서 execute()를 호출한 이 스레드(테스트 메인 스레드) 자체는
        // 즉시 리턴해야 한다 — 실제 task 실행(및 그 안의 acquire 대기)은 새로 뜬 가상스레드가 떠안는다.
        long startNanos = System.nanoTime();
        executor.execute(() -> { });
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

        assertThat(elapsedMillis).isLessThan(300);

        releaseFirst.countDown();
    }
}
