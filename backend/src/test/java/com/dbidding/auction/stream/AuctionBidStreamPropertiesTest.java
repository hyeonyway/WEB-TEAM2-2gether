package com.dbidding.auction.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AuctionBidStreamPropertiesTest {
    @Test
    void 기본_단건_소비_설정을_제공한다() {
        AuctionBidStreamProperties properties = new AuctionBidStreamProperties(null, null, 0, null, 0);

        assertThat(properties.block()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.claimIdle()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.maxRetries()).isEqualTo(3);
        assertThat(properties.consumerLockAtMostFor()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.maxRecordsPerRun()).isEqualTo(100);
    }
}
