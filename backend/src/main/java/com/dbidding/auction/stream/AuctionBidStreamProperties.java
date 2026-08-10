package com.dbidding.auction.stream;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auction.redis-bid")
public record AuctionBidStreamProperties(
        int batchSize,
        Duration block,
        Duration claimIdle,
        int maxRetries,
        Duration consumerLockAtMostFor
) {
    public AuctionBidStreamProperties {
        batchSize = batchSize == 0 ? 100 : batchSize;
        block = block == null ? Duration.ofSeconds(1) : block;
        claimIdle = claimIdle == null ? Duration.ofSeconds(30) : claimIdle;
        maxRetries = maxRetries == 0 ? 3 : maxRetries;
        consumerLockAtMostFor = consumerLockAtMostFor == null ? Duration.ofMinutes(5) : consumerLockAtMostFor;
        if (consumerLockAtMostFor.isNegative() || consumerLockAtMostFor.isZero()) {
            throw new IllegalArgumentException("consumerLockAtMostFor는 양수여야 합니다.");
        }
    }
}
