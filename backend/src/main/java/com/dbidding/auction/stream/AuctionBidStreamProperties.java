package com.dbidding.auction.stream;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auction.redis-bid")
public record AuctionBidStreamProperties(
        Duration block,
        Duration claimIdle,
        int maxRetries,
        Duration consumerLockAtMostFor,
        int maxRecordsPerRun
) {
    public AuctionBidStreamProperties {
        block = block == null ? Duration.ofSeconds(1) : block;
        claimIdle = claimIdle == null ? Duration.ofSeconds(30) : claimIdle;
        maxRetries = maxRetries == 0 ? 3 : maxRetries;
        consumerLockAtMostFor = consumerLockAtMostFor == null ? Duration.ofMinutes(5) : consumerLockAtMostFor;
        maxRecordsPerRun = maxRecordsPerRun == 0 ? 100 : maxRecordsPerRun;
        if (consumerLockAtMostFor.isNegative() || consumerLockAtMostFor.isZero()) {
            throw new IllegalArgumentException("consumerLockAtMostFor는 양수여야 합니다.");
        }
        if (maxRecordsPerRun < 1) {
            throw new IllegalArgumentException("maxRecordsPerRun은 1 이상이어야 합니다.");
        }
    }
}
