package com.dbidding.auction.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
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

    private TaskScheduler taskScheduler(String threadNamePrefix) {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix(threadNamePrefix);
        taskScheduler.setRemoveOnCancelPolicy(true);
        return taskScheduler;
    }
}
