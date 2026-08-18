package com.dbidding.auction.service;

import com.dbidding.auction.dto.AuctionResponses;
import com.dbidding.auction.dto.AuctionSearchRequest;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.dto.PageRequestDto;
import com.dbidding.auction.service.dblock.DbAuctionQueryService;
import com.dbidding.auction.service.redis.RedisAuctionQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Redis 실시간 상태(있으면)와 DB 조회({@link DbAuctionQueryService})를 오케스트레이션하는
 * 파사드. {@code redis} 프로필이 아니거나, 특정 경매가 Redis 실시간 상태에 없으면(오래된
 * seed-out 경매 등) DB 경로로 폴백한다.
 */
@Service
@RequiredArgsConstructor
public class AuctionQueryService {
    private final DbAuctionQueryService dbAuctionQueryService;
    @Autowired(required = false)
    private RedisAuctionQueryService redisAuctionQueryService;

    public AuctionResponses.CursorPage<AuctionResponses.AuctionSummary> search(
            Integer userId,
            AuctionSearchRequest request
    ) {
        return redisAuctionQueryService == null
                ? dbAuctionQueryService.search(userId, request)
                : redisAuctionQueryService.search(userId, request);
    }

    public List<AuctionResponses.DashboardAuction> getDashboardAuctions(Integer userId) {
        return dbAuctionQueryService.getDashboardAuctions(userId);
    }

    public List<AuctionResponses.FailedAuctionSummary> getFailedAuctions(Integer sellerId) {
        return dbAuctionQueryService.getFailedAuctions(sellerId);
    }

    public AuctionResponses.AuctionDetail getDetail(Integer userId, Integer auctionId) {
        AuctionResponses.AuctionDetail redisDetail = redisAuctionQueryService == null
                ? null : redisAuctionQueryService.getDetail(userId, auctionId);
        return redisDetail != null ? redisDetail : dbAuctionQueryService.getDetail(userId, auctionId);
    }

    public AuctionResponses.Page<BidResponses.BidSummary> getBids(Integer auctionId, PageRequestDto request) {
        AuctionResponses.Page<BidResponses.BidSummary> redisBids = redisAuctionQueryService == null
                ? null : redisAuctionQueryService.getBids(auctionId, request);
        return redisBids != null ? redisBids : dbAuctionQueryService.getBids(auctionId, request);
    }

    public BidResponses.BidContext getBidContext(Integer userId, Integer auctionId) {
        BidResponses.BidContext redisContext = redisAuctionQueryService == null
                ? null : redisAuctionQueryService.getBidContext(userId, auctionId);
        return redisContext != null ? redisContext : dbAuctionQueryService.getBidContext(userId, auctionId);
    }
}
