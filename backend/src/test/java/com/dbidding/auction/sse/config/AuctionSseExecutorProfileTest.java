package com.dbidding.auction.sse.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.sse.PerConnectionSseSendDispatcher;
import com.dbidding.sse.SynchronousSseSendDispatcher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AuctionSseExecutorProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(Clock.class, Clock::systemUTC)
            .withUserConfiguration(AuctionSseExecutorConfig.class);

    @Test
    void 기본_프로필에서는_bounded_ThreadPoolTaskExecutor를_쓴다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("auctionSseTaskExecutor")).isInstanceOf(ThreadPoolTaskExecutor.class);
            assertThat(context.getBean("auctionSseBroadcastTaskExecutor")).isInstanceOf(ThreadPoolTaskExecutor.class);
        });
    }

    @Test
    void sse_virtual_threads_프로필에서는_가상_스레드_executor를_쓴다() {
        contextRunner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("sse-virtual-threads"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean("auctionSseTaskExecutor")).isInstanceOf(SimpleAsyncTaskExecutor.class);
                    assertThat(context.getBean("auctionSseBroadcastTaskExecutor")).isInstanceOf(SimpleAsyncTaskExecutor.class);
                });
    }

    @Test
    void broadcast_executor는_send_executor와_별개의_빈이다() {
        // #507: 가상 스레드 사용 여부와 무관하게, 느린 클라이언트의 send가 broadcast() 호출자
        // 스레드를 막거나 caller-runs로 이어지지 않도록 두 executor는 항상 서로 다른 인스턴스여야 한다.
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("auctionSseBroadcastTaskExecutor"))
                    .isNotSameAs(context.getBean("auctionSseTaskExecutor"));
        });
        contextRunner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("sse-virtual-threads"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean("auctionSseBroadcastTaskExecutor"))
                            .isNotSameAs(context.getBean("auctionSseTaskExecutor"));
                });
    }

    @Test
    void 기본_프로필에서는_SynchronousSseSendDispatcher만_등록된다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("auctionSseSendDispatcher")).isInstanceOf(SynchronousSseSendDispatcher.class);
        });
    }

    @Test
    void sse_virtual_threads_프로필에서는_PerConnectionSseSendDispatcher가_등록된다() {
        contextRunner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("sse-virtual-threads"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean("auctionSseSendDispatcher")).isInstanceOf(PerConnectionSseSendDispatcher.class);
                });
    }
}
