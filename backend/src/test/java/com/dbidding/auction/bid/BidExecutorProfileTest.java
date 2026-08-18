package com.dbidding.auction.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.dbidding.auction.bid.dblock.DbBidExecutor;
import com.dbidding.auction.bid.redis.RedisBidExecutor;
import com.dbidding.auction.bid.redis.RedisBidLuaConfiguration;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.card.service.CardService;
import com.dbidding.order.service.OrderService;
import com.dbidding.wallet.service.WalletService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

class BidExecutorProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withBean(AuctionRepository.class, () -> mock(AuctionRepository.class))
            .withBean(BidRepository.class, () -> mock(BidRepository.class))
            .withBean(WalletService.class, () -> mock(WalletService.class))
            .withBean(AuctionEventPublisher.class, () -> mock(AuctionEventPublisher.class))
            .withBean(CardService.class, () -> mock(CardService.class))
            .withBean(OrderService.class, () -> mock(OrderService.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(AuctionMetrics.class, () -> new AuctionMetrics(new SimpleMeterRegistry()))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

    @Test
    void 기본_프로필에서는_DbBidExecutor만_등록된다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(BidExecutor.class);
            assertThat(context).hasSingleBean(DbBidExecutor.class);
            assertThat(context).doesNotHaveBean(RedisBidExecutor.class);
        });
    }

    @Test
    void 다른_운영_프로필만_활성화돼도_기본_DbBidExecutor가_등록된다() {
        contextRunner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("sse-virtual-threads"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DbBidExecutor.class);
                    assertThat(context).doesNotHaveBean(RedisBidExecutor.class);
                });
    }

    @Test
    void redis_프로필에서는_RedisBidExecutor만_등록된다() {
        contextRunner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("redis"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(BidExecutor.class);
                    assertThat(context).hasSingleBean(RedisBidExecutor.class);
                    assertThat(context).doesNotHaveBean(DbBidExecutor.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({DbBidExecutor.class, RedisBidExecutor.class, RedisBidLuaConfiguration.class})
    static class TestConfiguration {
    }
}
