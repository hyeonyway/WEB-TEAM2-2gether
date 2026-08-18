package com.dbidding.auction.service.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.service.AuctionCloseScheduleChangedEvent;
import com.dbidding.auction.service.EndingExtensionProvider;
import com.dbidding.auction.sse.AuctionStreamPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RedisAuctionEndingTransitionServiceTest {
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
    private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
    @SuppressWarnings("unchecked") private final RedisScript<String> script = mock(RedisScript.class);
    private final EndingExtensionProvider extensionProvider = mock(EndingExtensionProvider.class);
    private final AuctionMetrics auctionMetrics = mock(AuctionMetrics.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AuctionStreamPublisher auctionStreamPublisher = mock(AuctionStreamPublisher.class);

    @Test
    void due_경매만_전이하고_성공한_경매의_연장마감으로_재예약을_요청한다() {
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        RedisAuctionEndingTransitionService processor = new RedisAuctionEndingTransitionService(
                redisTemplate, script, extensionProvider, auctionMetrics, eventPublisher, auctionStreamPublisher
        );
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore("auction:ending-window:by-close-time", 0, now.toEpochMilli(), 0, 100))
                .thenReturn(new LinkedHashSet<>(List.of("1", "2")));
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("auction:state:1", "closeTime")).thenReturn("2026-08-10T00:05:00Z");
        when(hashOperations.get("auction:state:2", "closeTime")).thenReturn("2026-08-10T00:05:00Z");
        when(hashOperations.get("auction:state:1", "estimatedCloseTime")).thenReturn("2026-08-10T00:05:00Z");
        when(hashOperations.get("auction:state:2", "estimatedCloseTime")).thenReturn("2026-08-10T00:05:00Z");
        when(hashOperations.get("auction:state:1", "startPrice")).thenReturn("4.0000e+4");
        when(hashOperations.get("auction:state:1", "currentPrice")).thenReturn("4.3000e+4");
        when(hashOperations.get("auction:state:1", "bidIncrement")).thenReturn("3.000e+3");
        when(hashOperations.get("auction:state:1", "bidCount")).thenReturn("2");
        when(extensionProvider.next()).thenReturn(Duration.ofSeconds(90));
        when(redisTemplate.execute(eq(script), anyList(), eq("1"), eq("1786320000000"), eq("2026-08-10T00:00:00Z"),
                eq("2026-08-10T00:06:30Z"), eq("1786320390000"))).thenReturn("TRANSITIONED|1-0|2026-08-10T00:06:30Z");
        when(redisTemplate.execute(eq(script), anyList(), eq("2"), eq("1786320000000"), eq("2026-08-10T00:00:00Z"),
                eq("2026-08-10T00:06:30Z"), eq("1786320390000"))).thenReturn("NOOP|ENDING");

        assertThat(processor.transitionDueAuctions(now, 100)).containsExactly(1);

        verify(auctionMetrics).recordEndingTransition();
        verify(eventPublisher).publishEvent(new AuctionCloseScheduleChangedEvent(1, Instant.parse("2026-08-10T00:06:30Z"), "ending_transition"));
        verify(auctionStreamPublisher).publish(argThat(payload ->
                payload.type() == com.dbidding.auction.sse.AuctionStreamEventType.AUCTION_ENDING_STARTED
                        && payload.auctionId().equals(1)
                        && payload.startPrice().equals(40_000L)
                        && payload.currentPrice().equals(43_000L)
                        && payload.bidIncrement().equals(3_000L)
                        && payload.bidCount().equals(2)
                        && payload.endsAt().equals(Instant.parse("2026-08-10T00:05:00Z"))
                        && payload.status() == com.dbidding.auction.domain.AuctionStatus.ENDING
        ));
    }
}
