package com.dbidding.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.bid.RedisAuctionStateSeeder;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.auction.stream.RedisProjectionCatchUpVerifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisDashboardStateSeederTest {
    private final BidRepository bidRepository = mock(BidRepository.class);
    private final RedisAuctionStateSeeder auctionStateSeeder = mock(RedisAuctionStateSeeder.class);
    private final RedisProjectionCatchUpVerifier catchUpVerifier = mock(RedisProjectionCatchUpVerifier.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final RedisDashboardStateSeeder seeder =
            new RedisDashboardStateSeeder(bidRepository, auctionStateSeeder, catchUpVerifier, redisTemplate);

    @Test
    void 마커가_없으면_참여_경매를_시딩하고_TTL_없이_마커를_기록한다() {
        // bid-accept.lua가 매 입찰마다 auction:dashboard:participating을 실시간으로 갱신하므로,
        // 이 마커는 "최초 부트스트랩 여부"만 표시하면 된다 - TTL로 만료시키면 seedAllIfAbsent가
        // 드리프트를 고치지도 못하면서 콜드미스만 인위적으로 재발생시킨다.
        when(redisTemplate.hasKey("auction:dashboard:seeded:7")).thenReturn(false);
        when(catchUpVerifier.isCaughtUp()).thenReturn(true);
        when(bidRepository.findDistinctAuctionByBidderIdAndAuctionStatusIn(7, List.of(AuctionStatus.OPEN, AuctionStatus.ENDING)))
                .thenReturn(List.of());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        seeder.seedIfRequired(7);

        verify(valueOperations).set(eq("auction:dashboard:seeded:7"), eq("1"));
        verify(valueOperations, never()).set(any(), any(), any(java.time.Duration.class));
    }

    @Test
    void 마커가_있으면_다시_시딩하지_않는다() {
        when(redisTemplate.hasKey("auction:dashboard:seeded:7")).thenReturn(true);

        seeder.seedIfRequired(7);

        verify(bidRepository, never()).findDistinctAuctionByBidderIdAndAuctionStatusIn(any(), any());
    }
}
