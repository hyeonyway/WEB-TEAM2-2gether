package com.dbidding.auction.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

class AuctionStreamPublisherProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withBean(AuctionSseConnectionManager.class, () -> mock(AuctionSseConnectionManager.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void 기본_프로필에서는_RedisAuctionStreamPublisher만_등록된다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AuctionStreamPublisher.class);
            assertThat(context).hasSingleBean(RedisAuctionStreamPublisher.class);
            assertThat(context).doesNotHaveBean(LocalAuctionStreamPublisher.class);
        });
    }

    @Test
    void local_sse_프로필에서는_LocalAuctionStreamPublisher만_등록된다() {
        contextRunner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local-sse"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuctionStreamPublisher.class);
                    assertThat(context).hasSingleBean(LocalAuctionStreamPublisher.class);
                    assertThat(context).doesNotHaveBean(RedisAuctionStreamPublisher.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({LocalAuctionStreamPublisher.class, RedisAuctionStreamPublisher.class})
    static class TestConfiguration {
    }
}
