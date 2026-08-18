package com.dbidding.dashboard.service.redis;

import com.dbidding.auction.bid.redis.RedisAuctionStateSeeder;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.auction.stream.RedisProjectionCatchUpVerifier;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 대시보드 첫 조회에만 사용자의 활성 참여 경매를 Redis read model로 준비한다. */
@Component
@Profile("redis")
@RequiredArgsConstructor
public class RedisDashboardStateSeeder {
    private static final List<AuctionStatus> ACTIVE_STATUSES = List.of(AuctionStatus.OPEN, AuctionStatus.ENDING);

    private final BidRepository bidRepository;
    private final RedisAuctionStateSeeder auctionStateSeeder;
    private final RedisProjectionCatchUpVerifier projectionCatchUpVerifier;
    private final StringRedisTemplate redisTemplate;

    public void seedIfRequired(Integer userId) {
        String markerKey = "auction:dashboard:seeded:" + userId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(markerKey))) return;
        if (!projectionCatchUpVerifier.isCaughtUp()) throw AuctionException.stateRecoveryRequired();
        auctionStateSeeder.seedAllIfAbsent(
                bidRepository.findDistinctAuctionByBidderIdAndAuctionStatusIn(userId, ACTIVE_STATUSES)
        );
        redisTemplate.opsForValue().set(markerKey, "1");
    }
}
