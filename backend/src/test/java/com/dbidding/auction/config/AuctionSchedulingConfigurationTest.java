package com.dbidding.auction.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.service.AuctionClosingScheduler;
import com.dbidding.auction.service.AuctionCloseSchedulerProcessor;
import com.dbidding.auction.service.AuctionEndingTransitionProcessor;
import com.dbidding.auction.service.AuctionDueClosingService;
import com.dbidding.auction.service.AuctionCommandService;
import com.dbidding.auction.service.dblock.DbAuctionDeadlineScheduler;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;

class AuctionSchedulingConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
            .withUserConfiguration(
                    AuctionSchedulingConfig.class,
                    DbAuctionDeadlineScheduler.class,
                    AuctionClosingScheduler.class
            )
            .withBean(AuctionCloseSchedulerProcessor.class, () -> mock(AuctionCloseSchedulerProcessor.class))
            .withBean(AuctionEndingTransitionProcessor.class, () -> mock(AuctionEndingTransitionProcessor.class))
            .withBean(AuctionCommandService.class, () -> mock(AuctionCommandService.class))
            .withBean(AuctionRepository.class, () -> mock(AuctionRepository.class))
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void 실제_프로필에서도_정시와_백업_마감_스케줄러를_생성한다() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DbAuctionDeadlineScheduler.class);
            assertThat(context).hasSingleBean(AuctionClosingScheduler.class);
            assertThat(context).hasBean("auctionDeadlineTaskScheduler");
            assertThat(context).hasBean("auctionBackupTaskScheduler");
            assertThat(context).hasBean("taskScheduler");
            assertThat(context.getBean("auctionDeadlineTaskScheduler"))
                    .isInstanceOf(TaskScheduler.class);
            assertThat(context.getBean("auctionBackupTaskScheduler"))
                    .isInstanceOf(TaskScheduler.class)
                    .isNotSameAs(context.getBean("auctionDeadlineTaskScheduler"));
            assertThat(context.getBean(TaskScheduler.class))
                    .isSameAs(context.getBean("taskScheduler"))
                    .isNotSameAs(context.getBean("auctionDeadlineTaskScheduler"))
                    .isNotSameAs(context.getBean("auctionBackupTaskScheduler"));
        });
    }
}
