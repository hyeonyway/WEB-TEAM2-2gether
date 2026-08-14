package com.dbidding.auction.bid;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.service.AuctionEndingPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

class RedisAuctionStateWarmUpTest {
    @Test
    void warmUp이_비활성이어도_기존_OPEN_상태의_ending_인덱스를_보정한다() throws Exception {
        var auctionRepository = mock(com.dbidding.auction.repository.AuctionRepository.class);
        var stateSeeder = mock(RedisAuctionStateSeeder.class);
        var walletStateSeeder = mock(com.dbidding.wallet.service.RedisWalletStateSeeder.class);
        var redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        @SuppressWarnings("unchecked") HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(zSetOperations.range("auction:active:by-close-time", 0, 0)).thenReturn(Set.of("1"));
        long closeTimeEpochMillis = Instant.parse("2026-08-10T01:00:00Z").toEpochMilli();
        when(hashOperations.entries("auction:state:1")).thenReturn(Map.of(
                "status", "OPEN", "closeTime", "2026-08-10T01:00:00Z", "closeTimeEpochMillis", String.valueOf(closeTimeEpochMillis)
        ));
        RedisAuctionStateWarmUp warmUp = new RedisAuctionStateWarmUp(
                auctionRepository, stateSeeder, walletStateSeeder, redisTemplate, Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC)
        );

        warmUp.redisAuctionStateWarmUpRunner(false, 30, 100, 0, 1).run(null);

        verify(hashOperations).put("auction:state:1", "estimatedCloseTime", "2026-08-10T01:00:00Z");
        verify(zSetOperations).add(
                "auction:ending-window:by-close-time", "1", closeTimeEpochMillis - AuctionEndingPolicy.WINDOW.toMillis()
        );
    }

    @Test
    void warmUp된_경매의_낙찰_후보_지갑도_함께_시딩한다() throws Exception {
        var auctionRepository = mock(com.dbidding.auction.repository.AuctionRepository.class);
        var stateSeeder = mock(RedisAuctionStateSeeder.class);
        var walletStateSeeder = mock(com.dbidding.wallet.service.RedisWalletStateSeeder.class);
        var redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        @SuppressWarnings("unchecked") HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(auctionRepository.findActiveForWarmUp(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(java.util.List.of());
        when(auctionRepository.findByStatusInOrderByOpenTimeDesc(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(java.util.List.of());
        when(stateSeeder.seedAllIfAbsent(org.mockito.ArgumentMatchers.anyList())).thenReturn(java.util.List.of(2, 5));
        RedisAuctionStateWarmUp warmUp = new RedisAuctionStateWarmUp(
                auctionRepository, stateSeeder, walletStateSeeder, redisTemplate, Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC)
        );

        warmUp.redisAuctionStateWarmUpRunner(true, 30, 100, 0, 0).run(null);

        verify(walletStateSeeder).seedAllIfAbsent(java.util.List.of(2, 5));
    }

    @Test
    void 정렬_기준별_상위권_경매도_목록_커버리지용으로_함께_warmUp한다() throws Exception {
        var auctionRepository = mock(com.dbidding.auction.repository.AuctionRepository.class);
        var stateSeeder = mock(RedisAuctionStateSeeder.class);
        var walletStateSeeder = mock(com.dbidding.wallet.service.RedisWalletStateSeeder.class);
        var redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        @SuppressWarnings("unchecked") HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(auctionRepository.findByStatusInOrderByBidCountDesc(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(java.util.List.of());
        when(auctionRepository.findByStatusInOrderByCurrentPriceDesc(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(java.util.List.of());
        when(auctionRepository.findByStatusInOrderByCurrentPriceAsc(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(java.util.List.of());
        when(auctionRepository.findByStatusInOrderByChangeRateBasisPointsDesc(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(java.util.List.of());
        when(stateSeeder.seedAllIfAbsent(org.mockito.ArgumentMatchers.anyList())).thenReturn(java.util.List.of());
        RedisAuctionStateWarmUp warmUp = new RedisAuctionStateWarmUp(
                auctionRepository, stateSeeder, walletStateSeeder, redisTemplate, Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC)
        );

        warmUp.redisAuctionStateWarmUpRunner(true, 30, 0, 50, 0).run(null);

        verify(auctionRepository).findByStatusInOrderByBidCountDesc(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
        verify(auctionRepository).findByStatusInOrderByCurrentPriceDesc(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
        verify(auctionRepository).findByStatusInOrderByCurrentPriceAsc(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
        verify(auctionRepository).findByStatusInOrderByChangeRateBasisPointsDesc(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
    }
}
