package com.dbidding.auction.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AuctionBidStreamPropertiesTest {
    @Test
    void 기본_배치_소비_설정을_제공한다() {
        AuctionBidStreamProperties properties = new AuctionBidStreamProperties(0, null, null, 0);

        assertThat(properties.batchSize()).isEqualTo(100);
        assertThat(properties.block()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.claimIdle()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.maxRetries()).isEqualTo(3);
    }
}
