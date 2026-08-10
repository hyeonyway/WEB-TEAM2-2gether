package com.dbidding.auction.stream;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auction.redis-bid")
public record AuctionBidStreamProperties(
        int batchSize,
        Duration block,
        Duration claimIdle,
        int maxRetries
) {
    public AuctionBidStreamProperties {
        batchSize = batchSize == 0 ? 100 : batchSize;
        block = block == null ? Duration.ofSeconds(1) : block;
        claimIdle = claimIdle == null ? Duration.ofSeconds(30) : claimIdle;
        maxRetries = maxRetries == 0 ? 3 : maxRetries;
    }
}
