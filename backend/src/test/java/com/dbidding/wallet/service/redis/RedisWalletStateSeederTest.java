package com.dbidding.wallet.service.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.repository.AuctionTimelineEventRepository;
import com.dbidding.wallet.repository.WalletBootstrapRow;
import com.dbidding.wallet.repository.WalletRepository;
import com.dbidding.auction.stream.RedisProjectionCatchUpVerifier;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import com.dbidding.wallet.repository.WalletHoldRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisWalletStateSeederTest {
    private static final List<AuctionBidEventProjectionStatus> UNPROCESSED_STATUSES =
            List.of(AuctionBidEventProjectionStatus.PENDING, AuctionBidEventProjectionStatus.ERROR);
    @Test
    void Redis_지갑_state가_없을때만_MySQL_projection으로_초기화한다() {
        WalletRepository walletRepository = Mockito.mock(WalletRepository.class);
        WalletHoldRepository walletHoldRepository = Mockito.mock(WalletHoldRepository.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        RedisProjectionCatchUpVerifier projectionCatchUpVerifier = Mockito.mock(RedisProjectionCatchUpVerifier.class);
        RedisStateSingleFlight singleFlight = new RedisStateSingleFlight();
        @SuppressWarnings("unchecked") RedisScript<Long> script = Mockito.mock(RedisScript.class);
        WalletBootstrapRow row = Mockito.mock(WalletBootstrapRow.class);
        when(row.getUserId()).thenReturn(7);
        when(row.getPoint()).thenReturn(100_000L);
        when(row.getFrozenBalance()).thenReturn(30_000L);
        when(row.getProjectionVersion()).thenReturn(4L);
        when(walletRepository.findBootstrapRowsForUsers(List.of(7))).thenReturn(List.of(row));
        when(walletHoldRepository.findHeldRowsForUsers(List.of(7))).thenReturn(List.of());
        when(redisTemplate.hasKey("wallet:balance:7")).thenReturn(false);
        when(projectionCatchUpVerifier.isCaughtUpForUserFresh(7)).thenReturn(true);
        RedisWalletSeedBatchCoordinator batchCoordinator = new RedisWalletSeedBatchCoordinator(walletHoldRepository, walletRepository, 5, 200);

        new RedisWalletStateSeeder(
                walletRepository, walletHoldRepository, redisTemplate, projectionCatchUpVerifier, singleFlight, batchCoordinator, script
        ).seedIfAbsent(7);

        verify(redisTemplate).execute(script, List.of("wallet:balance:7"), "70000", "30000", "4");
    }

    @Test
    void seedAllIfAbsent은_caughtUp이_아니면_아무_쿼리도_하지_않는다() {
        WalletRepository walletRepository = Mockito.mock(WalletRepository.class);
        WalletHoldRepository walletHoldRepository = Mockito.mock(WalletHoldRepository.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        RedisProjectionCatchUpVerifier projectionCatchUpVerifier = Mockito.mock(RedisProjectionCatchUpVerifier.class);
        RedisStateSingleFlight singleFlight = new RedisStateSingleFlight();
        @SuppressWarnings("unchecked") RedisScript<Long> script = Mockito.mock(RedisScript.class);
        when(projectionCatchUpVerifier.isCaughtUp()).thenReturn(false);
        RedisWalletSeedBatchCoordinator batchCoordinator = new RedisWalletSeedBatchCoordinator(walletHoldRepository, walletRepository, 5, 200);

        new RedisWalletStateSeeder(
                walletRepository, walletHoldRepository, redisTemplate, projectionCatchUpVerifier, singleFlight, batchCoordinator, script
        ).seedAllIfAbsent(List.of(2, 5, 7));

        Mockito.verifyNoInteractions(walletRepository, walletHoldRepository);
    }

    @Test
    void seedAllIfAbsent은_userId_목록을_배치_쿼리_한_번으로_시딩한다() {
        WalletRepository walletRepository = Mockito.mock(WalletRepository.class);
        WalletHoldRepository walletHoldRepository = Mockito.mock(WalletHoldRepository.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        RedisProjectionCatchUpVerifier projectionCatchUpVerifier = Mockito.mock(RedisProjectionCatchUpVerifier.class);
        RedisStateSingleFlight singleFlight = new RedisStateSingleFlight();
        @SuppressWarnings("unchecked") RedisScript<Long> script = Mockito.mock(RedisScript.class);
        WalletBootstrapRow row2 = Mockito.mock(WalletBootstrapRow.class);
        when(row2.getUserId()).thenReturn(2);
        when(row2.getPoint()).thenReturn(50_000L);
        when(row2.getFrozenBalance()).thenReturn(0L);
        when(row2.getProjectionVersion()).thenReturn(1L);
        // userId 5는 wallet row가 아예 없는 유저 — 조용히 스킵되어야 함
        when(walletRepository.findBootstrapRowsForUsers(List.of(2, 5))).thenReturn(List.of(row2));
        when(walletHoldRepository.findHeldRowsForUsers(List.of(2, 5))).thenReturn(List.of());
        when(projectionCatchUpVerifier.isCaughtUp()).thenReturn(true);
        RedisWalletSeedBatchCoordinator batchCoordinator = new RedisWalletSeedBatchCoordinator(walletHoldRepository, walletRepository, 5, 200);

        new RedisWalletStateSeeder(
                walletRepository, walletHoldRepository, redisTemplate, projectionCatchUpVerifier, singleFlight, batchCoordinator, script
        ).seedAllIfAbsent(List.of(2, 5));

        verify(walletRepository, Mockito.times(1)).findBootstrapRowsForUsers(List.of(2, 5));
        verify(walletHoldRepository, Mockito.times(1)).findHeldRowsForUsers(List.of(2, 5));
        verify(redisTemplate).execute(script, List.of("wallet:balance:2"), "50000", "0", "1");
    }

    /** #573이 auction/order에서 고친 전역 오탐 503 문제를 wallet에도 확장 적용했는지 검증한다. */
    @Test
    void 관계없는_다른_유저의_PENDING_이벤트는_이_유저의_지갑_콜드시드를_막지_않는다() {
        WalletRepository walletRepository = Mockito.mock(WalletRepository.class);
        WalletHoldRepository walletHoldRepository = Mockito.mock(WalletHoldRepository.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        AuctionTimelineEventRepository eventRepository = Mockito.mock(AuctionTimelineEventRepository.class);
        RedisStateSingleFlight verifierSingleFlight = new RedisStateSingleFlight();
        Clock clock = Mockito.mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-08-18T00:00:00Z"));
        RedisProjectionCatchUpVerifier projectionCatchUpVerifier = new RedisProjectionCatchUpVerifier(
                redisTemplate, eventRepository, verifierSingleFlight, clock, Duration.ofMillis(500)
        );
        // 전역적으로는 다른 유저(사용자 99)의 이벤트가 PENDING으로 남아있지만, userId=7의 이력에는 없다.
        when(eventRepository.existsByProjectionStatus(AuctionBidEventProjectionStatus.PENDING)).thenReturn(true);
        when(eventRepository.existsByUserIdAndProjectionStatusIn(7, UNPROCESSED_STATUSES)).thenReturn(false);
        RedisStateSingleFlight seederSingleFlight = new RedisStateSingleFlight();
        @SuppressWarnings("unchecked") RedisScript<Long> script = Mockito.mock(RedisScript.class);
        WalletBootstrapRow row = Mockito.mock(WalletBootstrapRow.class);
        when(row.getUserId()).thenReturn(7);
        when(row.getPoint()).thenReturn(100_000L);
        when(row.getFrozenBalance()).thenReturn(30_000L);
        when(row.getProjectionVersion()).thenReturn(4L);
        when(walletRepository.findBootstrapRowsForUsers(List.of(7))).thenReturn(List.of(row));
        when(walletHoldRepository.findHeldRowsForUsers(List.of(7))).thenReturn(List.of());
        when(redisTemplate.hasKey("wallet:balance:7")).thenReturn(false);
        RedisWalletSeedBatchCoordinator batchCoordinator = new RedisWalletSeedBatchCoordinator(walletHoldRepository, walletRepository, 5, 200);

        new RedisWalletStateSeeder(
                walletRepository, walletHoldRepository, redisTemplate, projectionCatchUpVerifier, seederSingleFlight, batchCoordinator, script
        ).seedIfAbsent(7);

        verify(redisTemplate).execute(script, List.of("wallet:balance:7"), "70000", "30000", "4");
    }

    @Test
    void 이_유저_자신의_PENDING_이벤트가_있으면_여전히_지갑_콜드시드가_막힌다() {
        WalletRepository walletRepository = Mockito.mock(WalletRepository.class);
        WalletHoldRepository walletHoldRepository = Mockito.mock(WalletHoldRepository.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        AuctionTimelineEventRepository eventRepository = Mockito.mock(AuctionTimelineEventRepository.class);
        RedisStateSingleFlight verifierSingleFlight = new RedisStateSingleFlight();
        Clock clock = Mockito.mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-08-18T00:00:00Z"));
        RedisProjectionCatchUpVerifier projectionCatchUpVerifier = new RedisProjectionCatchUpVerifier(
                redisTemplate, eventRepository, verifierSingleFlight, clock, Duration.ofMillis(500)
        );
        when(eventRepository.existsByUserIdAndProjectionStatusIn(7, UNPROCESSED_STATUSES)).thenReturn(true);
        RedisStateSingleFlight seederSingleFlight = new RedisStateSingleFlight();
        @SuppressWarnings("unchecked") RedisScript<Long> script = Mockito.mock(RedisScript.class);
        when(redisTemplate.hasKey("wallet:balance:7")).thenReturn(false);
        RedisWalletSeedBatchCoordinator batchCoordinator = new RedisWalletSeedBatchCoordinator(walletHoldRepository, walletRepository, 5, 200);

        assertThatThrownBy(() -> new RedisWalletStateSeeder(
                walletRepository, walletHoldRepository, redisTemplate, projectionCatchUpVerifier, seederSingleFlight, batchCoordinator, script
        ).seedIfAbsent(7)).isInstanceOf(RuntimeException.class).hasCauseInstanceOf(AuctionException.class);
    }

    /**
     * #535 — 시딩 직전 캐시가 TTL 창 안에서 stale한 "caught up = true"를 그대로 신뢰하면, 그 순간
     * 막 도착한 이 유저의 PENDING 이벤트를 무시한 채 MySQL 버전으로 Redis sequence를 rewind시킬 수
     * 있다. seedIfAbsent가 fresh 재확인을 쓰는지 검증한다 — 캐시를 true로 데워둔 뒤 같은 TTL 창
     * 안에서 PENDING이 생겨도 콜드시드가 막혀야 한다.
     */
    @Test
    void 캐시가_TTL_창_안에서_stale_true여도_시딩_직전에는_fresh_재확인으로_막힌다() {
        WalletRepository walletRepository = Mockito.mock(WalletRepository.class);
        WalletHoldRepository walletHoldRepository = Mockito.mock(WalletHoldRepository.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        AuctionTimelineEventRepository eventRepository = Mockito.mock(AuctionTimelineEventRepository.class);
        RedisStateSingleFlight verifierSingleFlight = new RedisStateSingleFlight();
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-08-18T00:00:00Z"));
        Clock clock = Mockito.mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now.get());
        RedisProjectionCatchUpVerifier projectionCatchUpVerifier = new RedisProjectionCatchUpVerifier(
                redisTemplate, eventRepository, verifierSingleFlight, clock, Duration.ofMillis(500)
        );
        // 캐시를 caught-up=true로 데운다.
        when(eventRepository.existsByUserIdAndProjectionStatusIn(7, UNPROCESSED_STATUSES)).thenReturn(false);
        assertThat(projectionCatchUpVerifier.isCaughtUpForUser(7)).isTrue();
        // TTL(500ms) 창 안에서 이 유저의 새 PENDING 이벤트가 막 도착했다.
        now.set(now.get().plusMillis(100));
        when(eventRepository.existsByUserIdAndProjectionStatusIn(7, UNPROCESSED_STATUSES)).thenReturn(true);

        RedisStateSingleFlight seederSingleFlight = new RedisStateSingleFlight();
        @SuppressWarnings("unchecked") RedisScript<Long> script = Mockito.mock(RedisScript.class);
        when(redisTemplate.hasKey("wallet:balance:7")).thenReturn(false);
        RedisWalletSeedBatchCoordinator batchCoordinator = new RedisWalletSeedBatchCoordinator(walletHoldRepository, walletRepository, 5, 200);

        assertThatThrownBy(() -> new RedisWalletStateSeeder(
                walletRepository, walletHoldRepository, redisTemplate, projectionCatchUpVerifier, seederSingleFlight, batchCoordinator, script
        ).seedIfAbsent(7)).isInstanceOf(RuntimeException.class).hasCauseInstanceOf(AuctionException.class);
    }
}
