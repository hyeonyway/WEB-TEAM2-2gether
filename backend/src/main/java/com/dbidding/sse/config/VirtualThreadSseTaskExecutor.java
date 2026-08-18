package com.dbidding.sse.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

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
 * 역할을 한다.
 *
 * <p>permit 획득은 {@link #execute(Runnable)} 호출 스레드에서 {@link Semaphore#tryAcquire()}
 * (논블로킹)로 시도한다(#585). 예전엔(#575) 이 획득을 {@code super.execute()}가 새로
 * 띄우는 가상스레드 **안**에서 블로킹으로 했는데, 그러면 캡이 꽉 찼을 때도
 * {@code execute()} 호출자(broadcast() 순회 루프)는 안 막히지만 — task마다 가상스레드
 * 객체(+힙에 상주하는 StackChunk)는 permit 획득 여부와 무관하게 **일단 만들어지고 나서**
 * 대기하는 구조라, 제출 속도가 처리 속도를 넘어서면 대기 중인 가상스레드가 캡 값과
 * 무관하게 힙에 계속 쌓여 결국 힙 OOM으로 이어졌다(순수 SSE fan-out 부하테스트에서
 * 실측 확인, 구독자 2,000명 조건에서 캡을 걸어도 크래시 재현됨). 지금처럼 호출자
 * 스레드에서 {@code tryAcquire()}로 먼저 확인해 permit이 없으면 가상스레드를 아예
 * 만들지 않고 버리면(discard), "동시에 살아있는(대기 포함) 가상스레드 개수"가
 * permit 개수로 정확히 상한이 걸려 힙 고갈을 막으면서도, {@code tryAcquire()} 자체는
 * 항상 즉시 반환되므로 호출자가 안 막힌다는 #575의 목표도 그대로 유지된다.
 */
@Slf4j
public class VirtualThreadSseTaskExecutor extends SimpleAsyncTaskExecutor {

    private final AtomicInteger active = new AtomicInteger();
    private final Counter submitted;
    private final Counter completed;
    private final Counter failures;
    private final Counter discarded;
    private final Timer taskDuration;
    private final Semaphore concurrencyLimiter;
    private final String executorName;

    public VirtualThreadSseTaskExecutor(String threadNamePrefix, MeterRegistry registry, String executorName) {
        this(threadNamePrefix, registry, executorName, 0);
    }

    public VirtualThreadSseTaskExecutor(String threadNamePrefix, MeterRegistry registry, String executorName, int maxConcurrency) {
        super(threadNamePrefix);
        setVirtualThreads(true);
        this.executorName = executorName;
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
        // CountingDiscardPolicy/CountingCallerRunsPolicy와 같은 메트릭 이름을 재사용해
        // 기존 "CallerRuns 발생률(포화)" 대시보드 패널이 가상스레드 캡 discard도 함께 잡게 한다.
        this.discarded = Counter.builder("dbidding.sse.broadcast.saturated")
                .tag("executor", executorName)
                .description("캡이 꽉 차 discard로 유실된 virtual task 수")
                .register(registry);
        this.taskDuration = Timer.builder("dbidding.sse.executor.task.duration")
                .tags(tags)
                .description("virtual task 실행 시간")
                .publishPercentileHistogram()
                .register(registry);
    }

    @Override
    public void execute(Runnable task) {
        if (concurrencyLimiter != null && !concurrencyLimiter.tryAcquire()) {
            discarded.increment();
            log.warn("event=sse.executor.saturated executor={} action=discard active={}", executorName, active.get());
            return;
        }
        submitted.increment();
        super.execute(() -> {
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
