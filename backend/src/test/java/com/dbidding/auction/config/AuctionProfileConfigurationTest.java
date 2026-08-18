package com.dbidding.auction.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.auction.controller.AuctionController;
import com.dbidding.auction.service.AuctionClosingScheduler;
import com.dbidding.auction.service.AuctionCommandService;
import com.dbidding.auction.service.AuctionQueryService;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Profile;

class AuctionProfileConfigurationTest {

    @ParameterizedTest
    @MethodSource("profileNeutralAuctionComponents")
    void auctionComponentsAreAvailableRegardlessOfAdapterProfile(Class<?> componentType) {
        assertThat(componentType.getAnnotation(Profile.class)).isNull();
    }

    private static Stream<Class<?>> profileNeutralAuctionComponents() {
        return Stream.of(
                AuctionController.class,
                AuctionCommandService.class,
                AuctionQueryService.class,
                AuctionClosingScheduler.class,
                AuctionSchedulingConfig.class
        );
    }
}
