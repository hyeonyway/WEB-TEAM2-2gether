package com.dbidding.auction.service.redis;

import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.service.AuctionCloseScheduleChangedEvent;
import com.dbidding.auction.service.AuctionEndingTransitionProcessor;
import com.dbidding.auction.service.EndingExtensionProvider;
import com.dbidding.auction.sse.AuctionStreamPayload;
import com.dbidding.auction.sse.AuctionStreamPublisher;
import com.dbidding.global.redis.RedisIntegerValue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("redis")
@RequiredArgsConstructor
class RedisAuctionEndingTransitionService implements AuctionEndingTransitionProcessor {
    private static final String ENDING_WINDOW_BY_CLOSE_TIME = "auction:ending-window:by-close-time";
    private static final String ACTIVE_BY_CLOSE_TIME = "auction:active:by-close-time";
    private static final String TIMELINE_STREAM = "event:timeline";

    private final StringRedisTemplate redisTemplate;
    @Qualifier("auctionEndingTransitionScript") private final RedisScript<String> auctionEndingTransitionScript;
    private final EndingExtensionProvider extensionProvider;
    private final AuctionMetrics auctionMetrics;
    private final ApplicationEventPublisher eventPublisher;
    private final AuctionStreamPublisher auctionStreamPublisher;

    @Override
    public List<Integer> transitionDueAuctions(Instant now, int limit) {
        java.util.Set<String> auctionIds = redisTemplate.opsForZSet()
                .rangeByScore(ENDING_WINDOW_BY_CLOSE_TIME, 0, now.toEpochMilli(), 0, limit);
        if (auctionIds == null || auctionIds.isEmpty()) return List.of();
        List<Integer> transitioned = new ArrayList<>();
        for (String auctionId : auctionIds) {
            try {
                transition(Integer.valueOf(auctionId), now, transitioned);
            } catch (RuntimeException exception) {
                log.warn("event=auction.ending.redis_transition_failed auctionId={} now={}", auctionId, now, exception);
            }
        }
        return transitioned;
    }

    private void transition(Integer auctionId, Instant now, List<Integer> transitioned) {
        Object closeTimeValue = redisTemplate.opsForHash().get(stateKey(auctionId), "closeTime");
        if (closeTimeValue == null) return;
        Object estimatedCloseTimeValue = redisTemplate.opsForHash().get(stateKey(auctionId), "estimatedCloseTime");
        Instant estimatedCloseTime = estimatedCloseTimeValue == null ? Instant.parse(closeTimeValue.toString()) : Instant.parse(estimatedCloseTimeValue.toString());
        Instant newCloseTime = Instant.parse(closeTimeValue.toString()).plus(extensionProvider.next());
        String raw = redisTemplate.execute(auctionEndingTransitionScript,
                List.of(stateKey(auctionId), ENDING_WINDOW_BY_CLOSE_TIME, ACTIVE_BY_CLOSE_TIME, TIMELINE_STREAM),
                auctionId.toString(), Long.toString(now.toEpochMilli()), now.toString(),
                newCloseTime.toString(), Long.toString(newCloseTime.toEpochMilli()));
        if (raw == null || !raw.startsWith("TRANSITIONED|")) return;
        auctionMetrics.recordEndingTransition();
        transitioned.add(auctionId);
        eventPublisher.publishEvent(new AuctionCloseScheduleChangedEvent(auctionId, newCloseTime, "ending_transition"));
        auctionStreamPublisher.publish(AuctionStreamPayload.endingStarted(
                auctionId,
                longField(auctionId, "startPrice"),
                longField(auctionId, "currentPrice"),
                longField(auctionId, "bidIncrement"),
                intField(auctionId, "bidCount"),
                estimatedCloseTime,
                now
        ));
        log.info("event=auction.ending.transitioned auctionId={} estimatedCloseTime={} realCloseTime={} extensionSeconds={}",
                auctionId, closeTimeValue, newCloseTime, java.time.Duration.between(Instant.parse(closeTimeValue.toString()), newCloseTime).toSeconds());
    }

    private String stateKey(Integer auctionId) {
        return "auction:state:" + auctionId;
    }

    private long longField(Integer auctionId, String field) {
        Object value = redisTemplate.opsForHash().get(stateKey(auctionId), field);
        if (value == null) throw new IllegalStateException("Redis 경매 상태 필드가 없습니다: " + field);
        return RedisIntegerValue.parseLongExact(value.toString());
    }

    private int intField(Integer auctionId, String field) {
        Object value = redisTemplate.opsForHash().get(stateKey(auctionId), field);
        if (value == null) throw new IllegalStateException("Redis 경매 상태 필드가 없습니다: " + field);
        return Integer.parseInt(value.toString());
    }
}
