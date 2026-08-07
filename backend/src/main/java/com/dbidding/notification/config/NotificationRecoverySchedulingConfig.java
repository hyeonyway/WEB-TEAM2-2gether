package com.dbidding.notification.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class NotificationRecoverySchedulingConfig {

    @Bean
    @Qualifier("notificationRecoveryTaskScheduler")
    public TaskScheduler notificationRecoveryTaskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(2);
        taskScheduler.setThreadNamePrefix("notification-recovery-");
        taskScheduler.setRemoveOnCancelPolicy(true);
        return taskScheduler;
    }
}
