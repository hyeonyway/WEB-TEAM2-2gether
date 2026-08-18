package com.dbidding.auction.service.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.context.ApplicationEventPublisher;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.sse.AuctionStreamPublisher;
import com.dbidding.wallet.sse.WalletBalanceChangedEvent;

class RedisAuctionCloseSchedulerProcessorTest {
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
    @SuppressWarnings("unchecked")
    private final RedisScript<String> auctionCloseRequestScript = mock(RedisScript.class);
    private final AuctionEventPublisher auctionEventPublisher = mock(AuctionEventPublisher.class);
    private final AuctionStreamPublisher auctionStreamPublisher = mock(AuctionStreamPublisher.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final RedisAuctionCloseSchedulerProcessor processor = new RedisAuctionCloseSchedulerProcessor(
            redisTemplate, auctionCloseRequestScript, auctionEventPublisher, auctionStreamPublisher, eventPublisher
    );

    @Test
    void 종료된_경매_context를_갱신하고_종료_요청_event를_발행한다() {
        Instant now = Instant.parse("2026-08-12T01:00:00Z");
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore("auction:active:by-close-time", 0, now.toEpochMilli(), 0, 100))
                .thenReturn(new java.util.LinkedHashSet<>(List.of("11", "12")));
        when(redisTemplate.execute(eq(auctionCloseRequestScript), org.mockito.ArgumentMatchers.anyList(), eq("11"), eq(now.toString()), eq("1786496400000"), eq("1000000000000")))
                .thenReturn("ACCEPTED||0|7|10|리자몽||||40000|40000|3000|0|||");
        when(redisTemplate.execute(eq(auctionCloseRequestScript), org.mockito.ArgumentMatchers.anyList(), eq("12"), eq(now.toString()), eq("1786496400000"), eq("1000000000000")))
                .thenReturn("REPLAYED");

        assertThat(processor.processDueAuctions(now, 100)).containsExactly(11);
        verify(redisTemplate).execute(eq(auctionCloseRequestScript),
                eq(List.of("auction:state:11", "event:timeline", "auction:ending-window:by-close-time",
                        "auction:active:by-bid-count", "auction:active:by-price", "auction:active:by-change-rate", "auction:active:by-open-time")),
                eq("11"), eq(now.toString()), eq("1786496400000"), eq("1000000000000"));
        verify(auctionEventPublisher).publishClosed(argThat((AuctionClosedEvent event) ->
                event.auctionId().equals(11) && event.winnerId() == null && event.status() == com.dbidding.auction.domain.AuctionStatus.ENDED));
        verify(auctionStreamPublisher).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 낙찰_종료는_승인_직후_낙찰과_지갑_이벤트를_발행한다() {
        Instant now = Instant.parse("2026-08-12T01:00:00Z");
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore("auction:active:by-close-time", 0, now.toEpochMilli(), 0, 100))
                .thenReturn(new java.util.LinkedHashSet<>(List.of("11")));
        when(redisTemplate.execute(eq(auctionCloseRequestScript), org.mockito.ArgumentMatchers.anyList(),
                eq("11"), eq(now.toString()), eq("1786496400000"), eq("1000000000000")))
                .thenReturn("ACCEPTED|2|5.0000e+4|7|10|리자몽|10|JP|/thumb.png|4.0000e+4|5.0000e+4|3.000e+3|3|5.0000e+4|0|1.00000000000000e+14");

        assertThat(processor.processDueAuctions(now, 100)).containsExactly(11);
        verify(auctionEventPublisher).publishClosed(argThat((AuctionClosedEvent event) ->
                event.winnerId().equals(2) && event.winningPrice().equals(50_000L)));
        verify(eventPublisher).publishEvent(argThat((Object event) -> event instanceof WalletBalanceChangedEvent changed
                && changed.userId().equals(2) && changed.balance().frozenBalance() == 0L
                && changed.walletVersion() == 100_000_000_000_000L));
    }
}
