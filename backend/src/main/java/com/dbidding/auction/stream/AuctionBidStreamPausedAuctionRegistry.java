package com.dbidding.auction.stream;

import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 버전이 끊긴 경매의 후속 PEL 이벤트를 ACK하지 않고 보류한다.
 * 누락 이벤트가 정상 반영되면 해당 경매만 다시 소비 대상으로 전환한다.
 */
@Slf4j
@Component
@Profile("redis")
@RequiredArgsConstructor
public class AuctionBidStreamPausedAuctionRegistry {
    static final String KEY = "auction:bid-events:paused-auctions:v1";

    private final StringRedisTemplate redisTemplate;

    public boolean isPaused(Integer auctionId) {
        return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(KEY, String.valueOf(auctionId)));
    }

    public void pause(BidAcceptedStreamEvent event, BidStreamVersionGapException exception) {
        redisTemplate.opsForHash().put(KEY, String.valueOf(event.auctionId()), Map.of(
                "streamId", event.streamId(),
                "eventVersion", event.auctionVersion(),
                "expectedVersion", event.auctionVersion() - 1,
                "reason", exception.getMessage(),
                "pausedAt", Instant.now().toString()
        ).toString());
        log.error(
                "event=auction.bid.stream.auction_paused auctionId={} streamId={} eventVersion={} expectedVersion={}",
                event.auctionId(), event.streamId(), event.auctionVersion(), event.auctionVersion() - 1, exception
        );
    }

    public void resume(Integer auctionId) {
        redisTemplate.opsForHash().delete(KEY, String.valueOf(auctionId));
    }
}
