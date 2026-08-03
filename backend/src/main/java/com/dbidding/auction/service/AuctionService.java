package com.dbidding.auction.service;

import com.dbidding.auction.dto.AuctionCloseResponse;
import com.dbidding.auction.dto.AuctionCreateRequest;
import com.dbidding.auction.dto.AuctionCreateResponse;
import com.dbidding.auction.dto.AuctionResponses;
import com.dbidding.auction.dto.AuctionSearchRequest;
import com.dbidding.auction.dto.BidCreateRequest;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.dto.PageRequestDto;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuctionService {
    private final AuctionCommandService auctionCommandService;
    private final AuctionQueryService auctionQueryService;

    public AuctionCreateResponse create(Integer userId, AuctionCreateRequest request, String idempotencyKey) {
        return auctionCommandService.create(userId, request, idempotencyKey);
    }

    public BidResponses.BidResult participate(
            Integer userId,
            Integer auctionId,
            BidCreateRequest request,
            String idempotencyKey
    ) {
        return auctionCommandService.participate(userId, auctionId, request, idempotencyKey);
    }

    public AuctionResponses.Page<AuctionResponses.AuctionSummary> search(Integer userId, AuctionSearchRequest request) {
        return auctionQueryService.search(userId, request);
    }

    public AuctionResponses.AuctionDetail getDetail(Integer userId, Integer auctionId) {
        return auctionQueryService.getDetail(userId, auctionId);
    }

    public AuctionResponses.Page<BidResponses.BidSummary> getBids(Integer auctionId, PageRequestDto request) {
        return auctionQueryService.getBids(auctionId, request);
    }

    public BidResponses.BidContext getBidContext(Integer userId, Integer auctionId) {
        return auctionQueryService.getBidContext(userId, auctionId);
    }

    public AuctionCloseResponse closeAuction(Integer auctionId) {
        return auctionCommandService.closeAuction(auctionId);
    }

    public List<AuctionCloseResponse> closeDueAuctions(LocalDateTime now, int limit) {
        return auctionCommandService.closeDueAuctions(now, limit);
    }
}
