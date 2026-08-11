package com.dbidding.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.dbidding.notification.NotificationType;
import com.dbidding.notification.dto.NotificationResponse;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class LocalNotificationPushPublisherAsyncWiringVerifyTest {

    @Configuration
    @EnableAsync
    static class TestConfig {
        @Bean
        NotificationPushDispatcher pushDispatcher() {
            return mock(NotificationPushDispatcher.class);
        }

        @Bean
        LocalNotificationPushPublisher publisher(NotificationPushDispatcher pushDispatcher) {
            return new LocalNotificationPushPublisher(pushDispatcher);
        }

        @Bean(name = "notificationFanOutTaskExecutor")
        ThreadPoolTaskExecutor executor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setThreadNamePrefix("fanout-verify-");
            executor.initialize();
            return executor;
        }
    }

    @Test
    void 단건_default_메서드_호출도_fanOut_executor_스레드에서_실행된다() throws InterruptedException {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            NotificationPushDispatcher pushDispatcher = ctx.getBean(NotificationPushDispatcher.class);
            NotificationPushPublisher publisher = ctx.getBean(NotificationPushPublisher.class);
            AtomicReference<String> threadName = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
                return null;
            }).when(pushDispatcher).dispatch(anyInt(), any());

            publisher.publish(1, new NotificationResponse(
                    1L, 10, NotificationType.OUTBID, "메시지", false, Instant.parse("2026-08-11T00:00:00Z")));

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(threadName.get()).startsWith("fanout-verify-");
            assertThat(threadName.get()).isNotEqualTo(Thread.currentThread().getName());
        }
    }

    @Test
    void 배치_리스트_호출도_fanOut_executor_스레드에서_실행된다() throws InterruptedException {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            NotificationPushDispatcher pushDispatcher = ctx.getBean(NotificationPushDispatcher.class);
            NotificationPushPublisher publisher = ctx.getBean(NotificationPushPublisher.class);
            AtomicReference<String> threadName = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
                return null;
            }).when(pushDispatcher).dispatch(anyInt(), any());

            NotificationResponse payload = new NotificationResponse(
                    1L, 10, NotificationType.AUCTION_OPENED, "메시지", false, Instant.parse("2026-08-11T00:00:00Z"));
            publisher.publish(List.of(new NotificationPushMessage(1, payload)));

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(threadName.get()).startsWith("fanout-verify-");
            assertThat(threadName.get()).isNotEqualTo(Thread.currentThread().getName());
        }
    }
}
