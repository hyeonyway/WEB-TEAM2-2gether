package com.dbidding.auction.service;

import com.dbidding.auction.dto.AuctionCloseResponse;
import com.dbidding.auction.dto.AuctionCreateRequest;
import com.dbidding.auction.dto.AuctionCreateResponse;
import com.dbidding.auction.dto.AuctionResponses;
import com.dbidding.auction.dto.AuctionSearchRequest;
import com.dbidding.auction.dto.BidCreateRequest;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.dto.PageRequestDto;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.metrics.AuctionMetrics.BidResult;
import com.dbidding.auction.metrics.AuctionMetrics.CloseResult;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuctionService {
    private final AuctionCommandService auctionCommandService;
    private final AuctionQueryService auctionQueryService;
    private final AuctionMetrics auctionMetrics;

    public AuctionCreateResponse create(Integer userId, AuctionCreateRequest request, String idempotencyKey) {
        return auctionCommandService.create(userId, request, idempotencyKey);
    }

    public BidResponses.BidResult participate(
            Integer userId,
            Integer auctionId,
            BidCreateRequest request,
            String idempotencyKey
    ) {
        Timer.Sample sample = auctionMetrics.start();
        try {
            BidResponses.BidResult result = auctionCommandService.participate(
                    userId,
                    auctionId,
                    request,
                    idempotencyKey
            );
            auctionMetrics.finishBid(sample, BidResult.ACCEPTED);
            return result;
        } catch (ResponseStatusException exception) {
            auctionMetrics.finishBid(sample, BidResult.REJECTED);
            throw exception;
        } catch (RuntimeException exception) {
            auctionMetrics.finishBid(sample, BidResult.ERROR);
            throw exception;
        }
    }

    public AuctionResponses.CursorPage<AuctionResponses.AuctionSummary> search(
            Integer userId,
            AuctionSearchRequest request
    ) {
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
        Timer.Sample sample = auctionMetrics.start();
        try {
            AuctionCloseResponse response = auctionCommandService.closeAuction(auctionId);
            CloseResult result = response.winnerId() == null
                    ? CloseResult.WITHOUT_TRADE
                    : CloseResult.WITH_WINNER;
            auctionMetrics.finishClose(sample, result);
            return response;
        } catch (RuntimeException exception) {
            auctionMetrics.finishClose(sample, CloseResult.ERROR);
            throw exception;
        }
    }

    public List<AuctionCloseResponse> closeDueAuctions(LocalDateTime now, int limit) {
        return auctionCommandService.closeDueAuctions(now, limit);
    }
}
