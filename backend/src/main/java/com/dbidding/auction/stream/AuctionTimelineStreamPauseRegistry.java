package com.dbidding.auction.stream;

import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 버전 단절 같은 복구 대상 오류가 발생하면 전역 타임라인 소비를 명시적으로 멈춘다. */
@Slf4j
@Component
@Profile("redis")
@RequiredArgsConstructor
public class AuctionTimelineStreamPauseRegistry {
    static final String KEY = "auction:timeline-events:paused";

    private final StringRedisTemplate redisTemplate;

    public boolean isPaused() {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY));
    }

    public void pause(BidAcceptedStreamEvent event, Exception exception) {
        redisTemplate.opsForHash().putAll(KEY, Map.of(
                "streamId", event.streamId(),
                "auctionId", String.valueOf(event.auctionId()),
                "eventVersion", String.valueOf(event.auctionVersion()),
                "reason", String.valueOf(exception.getMessage()),
                "pausedAt", Instant.now().toString()
        ));
        log.error("event=auction.bid.stream.paused streamId={} auctionId={} eventVersion={}",
                event.streamId(), event.auctionId(), event.auctionVersion(), exception);
    }

    public void resume() {
        redisTemplate.delete(KEY);
    }
}
