package com.dbidding.auction.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AuctionSchedulingConfig {
    @Bean
    @Qualifier("auctionDeadlineTaskScheduler")
    public TaskScheduler auctionDeadlineTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("auction-deadline-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
