package com.dbidding.dashboard;

import com.dbidding.auction.bid.RedisAuctionStateSeeder;
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
        // TTL을 걸지 않는다: 첫 시딩 이후로는 bid-accept.lua가 매 입찰마다 실시간으로
        // auction:dashboard:participating을 갱신하므로, 이 마커가 만료돼 재조회가 발생해도
        // seedAllIfAbsent는 "없는 것만 채우는" 방식이라 드리프트를 고치지도 못하면서 콜드미스만
        // 인위적으로 만들어낸다 - 콜드미스 자체를 없애는 게 목표인 아키텍처와 반대 방향이다.
        redisTemplate.opsForValue().set(markerKey, "1");
    }
}
