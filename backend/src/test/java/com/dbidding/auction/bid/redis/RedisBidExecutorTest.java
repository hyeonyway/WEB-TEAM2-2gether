package com.dbidding.auction.bid.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.argThat;

import com.dbidding.auction.bid.dto.BidCommand;
import com.dbidding.auction.bid.dto.BidEventData;
import com.dbidding.auction.bid.dto.BidExecutionResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.context.ApplicationEventPublisher;
import com.dbidding.wallet.sse.WalletBalanceChangedEvent;
import com.dbidding.auction.exception.AuctionException;

@ExtendWith(MockitoExtension.class)
class RedisBidExecutorTest {
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private RedisScript<String> bidAcceptScript;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC);
    private RedisBidExecutor redisBidExecutor;

    @BeforeEach
    void setUp() {
        redisBidExecutor = new RedisBidExecutor(redisTemplate, bidAcceptScript, clock, null, null, eventPublisher);
    }

    @Test
    void Lua가_승인한_입찰의_eventId와_실시간_상태를_응답한다() {
        List<String> keys = List.of(
                "auction:state:1", "wallet:balance:2", "wallet:hold:1:2",
                "auction:bid:idempotency:1:2:bid-key", "event:timeline", "auction:ending-window:by-close-time",
                "auction:active:by-bid-count", "auction:active:by-price", "auction:active:by-change-rate", "auction:active:by-open-time"
        );
        when(redisTemplate.execute(eq(bidAcceptScript), eq(keys),
                eq("2"), eq("43000"), eq("bid-key"), anyString(), eq("1786320000000"),
                eq("2026-08-10T00:00:00Z"), eq("1000000000000")))
                .thenReturn("ACCEPTED|1700000000000-0|43000|7|3|57000|43000|1|46000|2026-08-10T01:00:00Z|LEADING||1|40000|3000|9|OPEN|false|리자몽|10|JP|/thumb.png|7|100000|0|5|false");

        var response = redisBidExecutor.execute(new BidCommand(2, 1, 43_000L, "bid-key"));

        assertThat(response.result().bid().id()).isNull();
        assertThat(response.result().bid().eventId()).isEqualTo("1700000000000-0");
        assertThat(response.result().bid().amount()).isEqualTo(43_000L);
        assertThat(response.result().auction().currentPrice()).isEqualTo(43_000L);
        assertThat(response.result().auction().minimumBid()).isEqualTo(46_000L);
        assertThat(response.result().auction().bidCount()).isEqualTo(3);
        assertThat(response.result().wallet().availableBalance()).isEqualTo(57_000L);
        assertThat(response.result().wallet().frozenBalance()).isEqualTo(43_000L);
        assertThat(response.eventData())
                .extracting(BidEventData::itemId, BidEventData::previousBidderId, BidEventData::startPrice, BidEventData::bidIncrement)
                .containsExactly(1, 9, 40_000L, 3_000L);
        assertThat(response.eventData().previousBidId()).isEqualTo(7L);
        verify(eventPublisher).publishEvent(argThat((Object event) -> event instanceof WalletBalanceChangedEvent changed
                && changed.userId().equals(2)
                && changed.balance().availableBalance() == 57_000L
                && changed.walletVersion() == 1L));
        verify(eventPublisher).publishEvent(argThat((Object event) -> event instanceof WalletBalanceChangedEvent changed
                && changed.userId().equals(9)
                && changed.balance().availableBalance() == 100_000L
                && changed.walletVersion() == 5L));
    }

    @Test
    void 멱등_재생_응답은_실시간_이벤트를_다시_발행하지_않는다() {
        when(redisTemplate.execute(eq(bidAcceptScript), org.mockito.ArgumentMatchers.anyList(),
                eq("2"), eq("43000"), eq("bid-key"), anyString(), eq("1786320000000"),
                eq("2026-08-10T00:00:00Z"), eq("1000000000000")))
                .thenReturn("ACCEPTED|1700000000000-0|43000|7|3|57000|43000|1|46000|2026-08-10T01:00:00Z|LEADING||1|40000|3000|9|OPEN|false|리자몽|10|JP|/thumb.png|7|100000|0|5|true");

        BidExecutionResult response = redisBidExecutor.execute(new BidCommand(2, 1, 43_000L, "bid-key"));

        assertThat(response.eventData()).isNull();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void ENDING_경매의_입찰_응답은_실제_연장_마감이_아닌_예정_마감을_반환한다() {
        when(redisTemplate.execute(eq(bidAcceptScript), org.mockito.ArgumentMatchers.anyList(),
                eq("2"), eq("43000"), eq("bid-key"), anyString(), eq("1786320000000"),
                eq("2026-08-10T00:00:00Z"), eq("1000000000000")))
                .thenReturn("ACCEPTED|1700000000000-0|43000|7|3|57000|43000|1|46000|2026-08-10T01:01:30Z|LEADING||1|40000|3000|9|ENDING|false|리자몽|10|JP|/thumb.png|7|100000|0|5|2026-08-10T01:00:00Z|false");

        BidExecutionResult response = redisBidExecutor.execute(new BidCommand(2, 1, 43_000L, "bid-key"));

        assertThat(response.result().auction().endsAt()).isEqualTo(Instant.parse("2026-08-10T01:00:00Z"));
    }

    @Test
    void 과거_멱등_응답의_지수_표기_금액을_exact_long으로_복구한다() {
        when(redisTemplate.execute(eq(bidAcceptScript), org.mockito.ArgumentMatchers.anyList(),
                eq("2"), eq("810000000000"), eq("legacy-key"), anyString(), eq("1786320000000"),
                eq("2026-08-10T00:00:00Z"), eq("1000000000000")))
                .thenReturn("ACCEPTED|1700000000000-0|8.1e+11|7|3|9e+10|8.1e+11|1e+14|8.2e+11|2026-08-10T01:00:00Z|LEADING||1|8e+11|1e+10|null|OPEN|false|리자몽|10|JP|/thumb.png|7||||true");

        BidExecutionResult response = redisBidExecutor.execute(
                new BidCommand(2, 1, 810_000_000_000L, "legacy-key"));

        assertThat(response.result().bid().amount()).isEqualTo(810_000_000_000L);
        assertThat(response.result().wallet().availableBalance()).isEqualTo(90_000_000_000L);
        assertThat(response.eventData()).isNull();
    }

    @Test
    void 입찰가가_1조원을_넘으면_Redis_호출_전에_거절한다() {
        assertThatThrownBy(() -> redisBidExecutor.execute(
                new BidCommand(2, 1, 1_000_000_000_001L, "over-price")))
                .isInstanceOf(AuctionException.class);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void Redis_입찰_응답의_지갑_합계가_long_범위를_넘으면_이벤트를_발행하지_않는다() {
        when(redisTemplate.execute(eq(bidAcceptScript), org.mockito.ArgumentMatchers.anyList(),
                eq("2"), eq("43000"), eq("bid-key"), anyString(), eq("1786320000000"),
                eq("2026-08-10T00:00:00Z"), eq("1000000000000")))
                .thenReturn("ACCEPTED|1700000000000-0|43000|7|3|9223372036854775807|1|1|46000|2026-08-10T01:00:00Z|LEADING||1|40000|3000|null|OPEN|false|리자몽|10|JP|/thumb.png|7||||false");

        assertThatThrownBy(() -> redisBidExecutor.execute(new BidCommand(2, 1, 43_000L, "bid-key")))
                .isInstanceOf(ArithmeticException.class);
        verifyNoInteractions(eventPublisher);
    }
}
