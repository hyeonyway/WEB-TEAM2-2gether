package com.dbidding.auction.bid.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.bid.dto.AuctionSeedData;
import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import com.dbidding.auction.repository.AuctionTimelineEventRepository;
import com.dbidding.auction.stream.RedisProjectionCatchUpVerifier;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RedisAuctionStateSeederTest {
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RedisProjectionCatchUpVerifier projectionCatchUpVerifier;
    @Mock private RedisStateSingleFlight singleFlight;
    @Mock private RedisAuctionSeedBatchCoordinator batchCoordinator;
    @Mock private RedisScript<Long> auctionStateSeedScript;
    @Captor private ArgumentCaptor<Object[]> arguments;

    @Test
    void Redis_시퀀스는_입찰수_대신_마지막_반영_이벤트_버전에서_시작한다() {
        Auction auction = Auction.builder()
                .sellerId(1).itemId(2).auctionName("경매").description("설명")
                .startPrice(10_000L).deliveryFee(0L).openTime(Instant.parse("2026-08-13T00:00:00Z"))
                .estimatedCloseTime(Instant.parse("2026-08-14T00:00:00Z"))
                .closeTime(Instant.parse("2026-08-14T00:00:00Z")).bidPriceUnit(1_000L).build();
        ReflectionTestUtils.setField(auction, "id", 3000005);
        ReflectionTestUtils.setField(auction, "bidCount", 3);
        ReflectionTestUtils.setField(auction, "lastBidEventVersion", 0L);
        given(projectionCatchUpVerifier.isCaughtUp()).willReturn(true);
        given(batchCoordinator.requestSeedData(3000005)).willReturn(CompletableFuture.completedFuture(
                Optional.of(new AuctionSeedData(
                        auction, null, new CardSnapshot(2, "카드", "세트", null, null, "thumbnail"),
                        List.of(), List.of(), List.of()))));
        given(redisTemplate.execute(eq(auctionStateSeedScript), anyList(), any(Object[].class))).willReturn(1L);

        new RedisAuctionStateSeeder(redisTemplate, projectionCatchUpVerifier, singleFlight,
                batchCoordinator, auctionStateSeedScript)
                .seedAllIfAbsent(List.of(auction));

        verify(redisTemplate).execute(eq(auctionStateSeedScript), anyList(), arguments.capture());
        List<Object> args = List.of(arguments.getValue());
        assertThat(args.get(args.indexOf("sequence") + 1)).isEqualTo("0");
        assertThat(args.get(args.indexOf("bidCount") + 1)).isEqualTo("3");
    }

    @Test
    void seedIfAbsent는_그_경매_단위로만_catch_up을_확인한다() {
        RedisAuctionStateSeeder seeder = new RedisAuctionStateSeeder(redisTemplate, projectionCatchUpVerifier,
                new RedisStateSingleFlight(), batchCoordinator, auctionStateSeedScript);
        given(projectionCatchUpVerifier.isCaughtUpForAuctionFresh(3000005)).willReturn(false);

        assertThatThrownBy(() -> seeder.seedIfAbsent(3000005)).isInstanceOf(RuntimeException.class);

        verify(projectionCatchUpVerifier).isCaughtUpForAuctionFresh(3000005);
        verify(projectionCatchUpVerifier, org.mockito.Mockito.never()).isCaughtUp();
    }

    @Test
    void seedIfAbsent는_catch_up이_확인되면_배치_코디네이터로_시딩한다() {
        Auction auction = Auction.builder()
                .sellerId(1).itemId(2).auctionName("경매").description("설명")
                .startPrice(10_000L).deliveryFee(0L).openTime(Instant.parse("2026-08-13T00:00:00Z"))
                .estimatedCloseTime(Instant.parse("2026-08-14T00:00:00Z"))
                .closeTime(Instant.parse("2026-08-14T00:00:00Z")).bidPriceUnit(1_000L).build();
        ReflectionTestUtils.setField(auction, "id", 3000006);
        ReflectionTestUtils.setField(auction, "bidCount", 0);
        ReflectionTestUtils.setField(auction, "lastBidEventVersion", 0L);
        RedisAuctionStateSeeder seeder = new RedisAuctionStateSeeder(redisTemplate, projectionCatchUpVerifier,
                new RedisStateSingleFlight(), batchCoordinator, auctionStateSeedScript);
        given(projectionCatchUpVerifier.isCaughtUpForAuctionFresh(3000006)).willReturn(true);
        given(batchCoordinator.requestSeedData(3000006)).willReturn(CompletableFuture.completedFuture(
                Optional.of(new AuctionSeedData(
                        auction, null, new CardSnapshot(2, "카드", "세트", null, null, "thumbnail"),
                        List.of(), List.of(), List.of()))));
        given(redisTemplate.execute(eq(auctionStateSeedScript), anyList(), any(Object[].class))).willReturn(1L);

        boolean seeded = seeder.seedIfAbsent(3000006);

        assertThat(seeded).isTrue();
    }

    /**
     * #535 — 시딩 직전 캐시가 TTL 창 안에서 stale한 "caught up = true"를 그대로 신뢰하면, 그 순간
     * 막 도착한 이 경매의 PENDING 이벤트를 무시한 채 MySQL의 lastBidEventVersion으로 Redis
     * sequence를 rewind시킬 수 있다. seedIfAbsent가 isCaughtUpForAuctionFresh로 캐시를 우회하는지
     * 실제 RedisProjectionCatchUpVerifier를 통해 검증한다.
     */
    @Test
    void 캐시가_TTL_창_안에서_stale_true여도_시딩_직전에는_fresh_재확인으로_막힌다() {
        AuctionTimelineEventRepository eventRepository = Mockito.mock(AuctionTimelineEventRepository.class);
        List<AuctionBidEventProjectionStatus> unprocessedStatuses =
                List.of(AuctionBidEventProjectionStatus.PENDING, AuctionBidEventProjectionStatus.ERROR);
        java.util.concurrent.atomic.AtomicReference<Instant> now =
                new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-08-18T00:00:00Z"));
        Clock clock = Mockito.mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now.get());
        RedisProjectionCatchUpVerifier realVerifier = new RedisProjectionCatchUpVerifier(
                redisTemplate, eventRepository, new RedisStateSingleFlight(), clock, Duration.ofMillis(500)
        );
        // 캐시를 caught-up=true로 데운다.
        when(eventRepository.existsByAuctionIdAndProjectionStatusIn(3000007, unprocessedStatuses)).thenReturn(false);
        assertThat(realVerifier.isCaughtUpForAuction(3000007)).isTrue();
        // TTL(500ms) 창 안에서 이 경매의 새 PENDING 이벤트가 막 도착했다.
        now.set(now.get().plusMillis(100));
        when(eventRepository.existsByAuctionIdAndProjectionStatusIn(3000007, unprocessedStatuses)).thenReturn(true);

        RedisAuctionStateSeeder seeder = new RedisAuctionStateSeeder(redisTemplate, realVerifier,
                new RedisStateSingleFlight(), batchCoordinator, auctionStateSeedScript);

        assertThatThrownBy(() -> seeder.seedIfAbsent(3000007)).isInstanceOf(RuntimeException.class);
    }
}
