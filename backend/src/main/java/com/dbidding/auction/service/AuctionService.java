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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("auction-mock")
@RequiredArgsConstructor
public class AuctionService {
    private final AuctionCommandService auctionCommandService;
    private final AuctionQueryService auctionQueryService;

    public AuctionCreateResponse create(AuctionCreateRequest request, String idempotencyKey) {
        return auctionCommandService.create(request, idempotencyKey);
    }

    public BidResponses.BidSummary participate(Integer auctionId, BidCreateRequest request, String idempotencyKey) {
        return auctionCommandService.participate(auctionId, request, idempotencyKey);
    }

    public AuctionResponses.Page<AuctionResponses.AuctionSummary> search(AuctionSearchRequest request) {
        return auctionQueryService.search(request);
    }

    public AuctionResponses.AuctionDetail getDetail(Integer auctionId) {
        return auctionQueryService.getDetail(auctionId);
    }

    public AuctionResponses.Page<BidResponses.BidSummary> getBids(Integer auctionId, PageRequestDto request) {
        return auctionQueryService.getBids(auctionId, request);
    }

    public BidResponses.BidContext getBidContext(Integer auctionId) {
        return auctionQueryService.getBidContext(auctionId);
    }

    public AuctionCloseResponse closeAuction(Integer auctionId) {
        return auctionCommandService.closeAuction(auctionId);
    }

    public List<AuctionCloseResponse> closeDueAuctions(LocalDateTime now, int limit) {
        return auctionCommandService.closeDueAuctions(now, limit);
    }
}
