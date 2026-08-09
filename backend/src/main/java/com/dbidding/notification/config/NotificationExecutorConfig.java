package com.dbidding.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@Slf4j
public class NotificationExecutorConfig {
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
        executor.setRejectedExecutionHandler((task, threadPool) -> {
            if (threadPool.isShutdown()) {
                log.warn("event=notification.executor.rejected reason=shutdown");
                return;
            }
            log.warn("event=notification.executor.saturated action=caller_runs activeCount={} queueSize={}",
                    threadPool.getActiveCount(), threadPool.getQueue().size());
            task.run();
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
