package com.dbidding.notification.config;

import com.dbidding.sse.config.CountingCallerRunsPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
}
