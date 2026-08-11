package com.dbidding.auction.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;

class AuctionSseSendDispatcherProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withBean("auctionSseTaskExecutor", TaskExecutor.class, () -> mock(TaskExecutor.class));

    @Test
    void 기본_프로필에서는_SynchronousAuctionSseSendDispatcher만_등록된다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AuctionSseSendDispatcher.class);
            assertThat(context).hasSingleBean(SynchronousAuctionSseSendDispatcher.class);
            assertThat(context).doesNotHaveBean(PerConnectionAuctionSseSendDispatcher.class);
        });
    }

    @Test
    void sse_virtual_threads_프로필에서는_PerConnectionAuctionSseSendDispatcher만_등록된다() {
        contextRunner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("sse-virtual-threads"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuctionSseSendDispatcher.class);
                    assertThat(context).hasSingleBean(PerConnectionAuctionSseSendDispatcher.class);
                    assertThat(context).doesNotHaveBean(SynchronousAuctionSseSendDispatcher.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({SynchronousAuctionSseSendDispatcher.class, PerConnectionAuctionSseSendDispatcher.class})
    static class TestConfiguration {
    }
}
