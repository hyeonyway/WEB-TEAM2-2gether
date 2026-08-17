package com.dbidding.notification.config;

import com.dbidding.sse.config.CountingCallerRunsPolicy;
import com.dbidding.sse.config.CountingDiscardPolicy;
import com.dbidding.sse.config.VirtualThreadSseTaskExecutor;
import com.dbidding.sse.metrics.SseMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@Slf4j
@RequiredArgsConstructor
public class NotificationExecutorConfig {
    private final MeterRegistry meterRegistry;
    @Value("${NOTIFICATION_CORE_POOL_SIZE:4}")
    private int corePoolSize;

    @Value("${NOTIFICATION_MAX_POOL_SIZE:8}")
    private int maxPoolSize;

    @Value("${NOTIFICATION_QUEUE_CAPACITY:2000}")
    private int queueCapacity;

    @Value("${NOTIFICATION_FANOUT_CORE_POOL_SIZE:4}")
    private int fanOutCorePoolSize;

    @Value("${NOTIFICATION_FANOUT_MAX_POOL_SIZE:8}")
    private int fanOutMaxPoolSize;

    @Value("${NOTIFICATION_FANOUT_QUEUE_CAPACITY:2000}")
    private int fanOutQueueCapacity;

    /** {@code sse-virtual-threads} 전용 — 0이면 무제한(기본값), 양수면 동시 실행 개수를 이 값으로 제한한다. */
    @Value("${NOTIFICATION_FANOUT_VIRTUAL_MAX_CONCURRENCY:0}")
    private int fanOutVirtualMaxConcurrency;

    /**
     * origin(저장+발행, {@code NotificationEventListener}) 전용 — {@code @TransactionalEventListener(AFTER_COMMIT)}라
     * 커밋한 스레드(입찰/주문 등 API 요청 스레드일 수 있음)가 caller다. CallerRunsPolicy를 쓰면 포화 시
     * 그 요청 스레드가 알림 저장(DB write)+발행을 직접 떠안아 API 지연과 HikariCP 풀 압박이 겹친다.
     * 알림은 별도 복구 로직으로 유실 없이 목록에 남으므로(SSE 실시간 push만 재시도 안 됨),
     * discard로 API 스레드를 보호한다.
     */
    @Bean(name = "notificationTaskExecutor")
    public ThreadPoolTaskExecutor notificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("notification-");
        executor.setRejectedExecutionHandler(new CountingDiscardPolicy(meterRegistry, "notification"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * subscriber(로컬 fan-out, {@code NotificationPushRedisSubscriber}/{@code LocalNotificationPushPublisher}) 전용
     * — DB 접근 없이 순수 네트워크 SSE send만 하는 작업이라 origin과 풀을 공유하지 않는다(#305).
     *
     * <p>{@code MeSseConnectionManager.heartbeat()}(#557)도 이 executor를 빌려 쓴다 — 알림·지갑
     * SSE 커넥션이 공유되면서 이 pool이 이제 알림 fan-out 물량뿐 아니라 지갑 커넥션까지 포함한
     * 전체 heartbeat 물량도 감당한다. {@code NOTIFICATION_FANOUT_*}로 용량을 조정할 때 이 점을
     * 감안해야 한다.
     */
    @Bean(name = "notificationFanOutTaskExecutor")
    @Profile("!sse-virtual-threads")
    public TaskExecutor notificationFanOutTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(fanOutCorePoolSize);
        executor.setMaxPoolSize(fanOutMaxPoolSize);
        executor.setQueueCapacity(fanOutQueueCapacity);
        executor.setThreadNamePrefix("notification-fanout-");
        executor.setRejectedExecutionHandler(new CountingCallerRunsPolicy(meterRegistry, "notification-fanout"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * {@code sse-virtual-threads} 프로필 전용 — 위와 동일한 워크로드를 가상 스레드로
     * 처리한다(#362). 공유 자원(DB 등)이 없는 순수 네트워크 fan-out이라 풀 상한이
     * 방화벽 역할을 하지 않고, 유저 1개당 독립 task로 세분화해도(디스패처 참고)
     * 스레드 고갈 위험이 없다.
     */
    @Bean(name = "notificationFanOutTaskExecutor")
    @Profile("sse-virtual-threads")
    public TaskExecutor notificationFanOutVirtualTaskExecutor() {
        return new VirtualThreadSseTaskExecutor(
                "notification-fanout-", meterRegistry, "notification-sse", fanOutVirtualMaxConcurrency);
    }

    /** #508 — {@code NotificationSseConnectionManager}의 메트릭 배선. */
    @Bean(name = "notificationSseMetrics")
    public SseMetrics notificationSseMetrics(Clock clock) {
        return new SseMetrics(meterRegistry, "notification", clock);
    }
}
