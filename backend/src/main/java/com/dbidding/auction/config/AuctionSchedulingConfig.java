package com.dbidding.auction.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AuctionSchedulingConfig {
    @Bean
    @Qualifier("auctionDeadlineTaskScheduler")
    public TaskScheduler auctionDeadlineTaskScheduler() {
        return taskScheduler("auction-deadline-");
    }

    @Bean
    @Qualifier("auctionBackupTaskScheduler")
    public TaskScheduler auctionBackupTaskScheduler() {
        return taskScheduler("auction-backup-");
    }

    @Bean
    @Primary
    public TaskScheduler taskScheduler() {
        return taskScheduler("application-scheduling-");
    }

    @Bean(name = "auctionCloseTaskExecutor")
    public ThreadPoolTaskExecutor auctionCloseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("auction-close-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    private TaskScheduler taskScheduler(String threadNamePrefix) {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix(threadNamePrefix);
        taskScheduler.setRemoveOnCancelPolicy(true);
        return taskScheduler;
    }
}
