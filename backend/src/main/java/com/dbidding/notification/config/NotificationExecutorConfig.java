package com.dbidding.notification.config;

import com.dbidding.sse.config.CountingCallerRunsPolicy;
import com.dbidding.sse.config.VirtualThreadSseTaskExecutor;
import io.micrometer.core.instrument.MeterRegistry;
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

    /**
     * origin(저장+발행, {@code NotificationEventListener}) 전용 — DB 커넥션(HikariCP)을 쓰는
     * 작업이라 동시 실행 상한이 방화벽 역할을 한다.
     */
    @Bean(name = "notificationTaskExecutor")
    public ThreadPoolTaskExecutor notificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("notification-");
        executor.setRejectedExecutionHandler(new CountingCallerRunsPolicy(meterRegistry, "notification"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * subscriber(로컬 fan-out, {@code NotificationPushRedisSubscriber}/{@code LocalNotificationPushPublisher}/
     * {@code NotificationSseConnectionManager.heartbeat()}) 전용 — DB 접근 없이 순수 네트워크
     * SSE send만 하는 작업이라 origin과 풀을 공유하지 않는다(#305).
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
        return new VirtualThreadSseTaskExecutor("notification-fanout-", meterRegistry, "notification-sse");
    }
}
