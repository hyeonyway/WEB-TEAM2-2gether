package com.dbidding.sse.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * 가상 스레드 per-task executor에는 고정 pool/queue가 없어 platform executor와 같은
 * Gauge를 만드는 것이 의미가 없다. 대신 {@link #execute(Runnable)} 제출 경계를 감싸
 * task lifecycle(제출/실행중/완료/실패/소요시간)을 계측한다.
 *
 * <p>{@code SimpleAsyncTaskExecutor}를 상속해 반환 타입과 스레드 이름 접두사 동작은
 * 그대로 유지한다.
 */
public class VirtualThreadSseTaskExecutor extends SimpleAsyncTaskExecutor {

    private final AtomicInteger active = new AtomicInteger();
    private final Counter submitted;
    private final Counter completed;
    private final Counter failures;
    private final Timer taskDuration;

    public VirtualThreadSseTaskExecutor(String threadNamePrefix, MeterRegistry registry, String executorName) {
        super(threadNamePrefix);
        setVirtualThreads(true);
        Tags tags = Tags.of("executor", executorName, "thread_type", "virtual");
        this.submitted = Counter.builder("dbidding.sse.executor.submitted")
                .tags(tags)
                .description("제출된 virtual task 누적 수")
                .register(registry);
        Gauge.builder("dbidding.sse.executor.active", active, AtomicInteger::get)
                .tags(tags)
                .description("현재 실행 중인 virtual task 수")
                .register(registry);
        this.completed = Counter.builder("dbidding.sse.executor.completed")
                .tags(tags)
                .description("정상 종료된 virtual task 누적 수")
                .register(registry);
        this.failures = Counter.builder("dbidding.sse.executor.failures")
                .tags(tags)
                .description("실행 중 예외로 종료된 task 누적 수")
                .register(registry);
        this.taskDuration = Timer.builder("dbidding.sse.executor.task.duration")
                .tags(tags)
                .description("virtual task 실행 시간")
                .publishPercentileHistogram()
                .register(registry);
    }

    @Override
    public void execute(Runnable task) {
        submitted.increment();
        active.incrementAndGet();
        long startNanos = System.nanoTime();
        super.execute(() -> {
            try {
                task.run();
                completed.increment();
            } catch (RuntimeException | Error exception) {
                failures.increment();
                throw exception;
            } finally {
                active.decrementAndGet();
                taskDuration.record(Duration.ofNanos(System.nanoTime() - startNanos));
            }
        });
    }
}
