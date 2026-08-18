package com.dbidding.sse.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

/**
 * 가상 스레드 per-task executor에는 고정 pool/queue가 없어 platform executor와 같은
 * Gauge를 만드는 것이 의미가 없다. 대신 {@link #execute(Runnable)} 제출 경계를 감싸
 * task lifecycle(제출/실행중/완료/실패/소요시간)을 계측한다.
 *
 * <p>{@code SimpleAsyncTaskExecutor}를 상속해 반환 타입과 스레드 이름 접두사 동작은
 * 그대로 유지한다.
 *
 * <p>{@code maxConcurrency > 0}이면 세마포어로 동시 실행 개수를 제한한다 — 가상
 * 스레드는 생성 비용이 거의 없어 무제한으로 뜨면 순간적으로 CPU 코어를 전부
 * 점유해버릴 수 있는데(SSE 브로드캐스트 fan-out이 bid 처리용 CPU를 잠식하는
 * 문제), 이 permit이 backend CPU를 SSE와 다른 작업 사이에서 나누는 손잡이
 * 역할을 한다. permit 획득(블로킹)은 {@link #execute(Runnable)} 호출 스레드가 아니라
 * {@code super.execute()}가 새로 띄우는 가상스레드 안에서 한다(#575) — 그래야
 * {@code execute()}를 부르는 쪽(대개 {@code broadcast()}의 emitter 순회 루프)이
 * 캡이 꽉 찼을 때도 안 막히고 바로 다음 emitter로 넘어갈 수 있다. 예전엔 이
 * acquire()가 execute() 호출 스레드 자체에서 블로킹해서, 순회 루프 하나가
 * "실행 중인 task 하나 끝날 때까지" 매번 멈춰 서는 문제가 있었다(순수 SSE
 * fan-out 부하테스트에서 캡을 걸수록 오히려 배달 지연이 나빠지는 현상으로 확인됨).
 */
public class VirtualThreadSseTaskExecutor extends SimpleAsyncTaskExecutor {

    private final AtomicInteger active = new AtomicInteger();
    private final Counter submitted;
    private final Counter completed;
    private final Counter failures;
    private final Timer taskDuration;
    private final Semaphore concurrencyLimiter;

    public VirtualThreadSseTaskExecutor(String threadNamePrefix, MeterRegistry registry, String executorName) {
        this(threadNamePrefix, registry, executorName, 0);
    }

    public VirtualThreadSseTaskExecutor(String threadNamePrefix, MeterRegistry registry, String executorName, int maxConcurrency) {
        super(threadNamePrefix);
        setVirtualThreads(true);
        this.concurrencyLimiter = maxConcurrency > 0 ? new Semaphore(maxConcurrency) : null;
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
        super.execute(() -> {
            if (concurrencyLimiter != null) {
                try {
                    concurrencyLimiter.acquire();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new TaskRejectedException("SSE 브로드캐스트 permit 대기 중 인터럽트됨", exception);
                }
            }
            active.incrementAndGet();
            long startNanos = System.nanoTime();
            try {
                task.run();
                completed.increment();
            } catch (RuntimeException | Error exception) {
                failures.increment();
                throw exception;
            } finally {
                active.decrementAndGet();
                taskDuration.record(Duration.ofNanos(System.nanoTime() - startNanos));
                if (concurrencyLimiter != null) concurrencyLimiter.release();
            }
        });
    }
}
