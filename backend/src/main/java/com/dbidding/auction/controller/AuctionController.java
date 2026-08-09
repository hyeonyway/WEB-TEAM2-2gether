package com.dbidding.auction.controller;

import com.dbidding.auction.dto.AuctionCreateRequest;
import com.dbidding.auction.dto.AuctionCreateResponse;
import com.dbidding.auction.dto.AuctionResponses;
import com.dbidding.auction.dto.AuctionSearchRequest;
import com.dbidding.auction.dto.BidCreateRequest;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.dto.PageRequestDto;
import com.dbidding.auction.service.AuctionCommandService;
import com.dbidding.auction.service.AuctionQueryService;
import com.dbidding.global.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionController {
    private final AuctionCommandService auctionCommandService;
    private final AuctionQueryService auctionQueryService;

    @PostMapping
    public ResponseEntity<AuctionCreateResponse> create(
            @CurrentUser Integer userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AuctionCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(auctionCommandService.create(userId, request, idempotencyKey));
    }

    @PostMapping("/{auctionId}/bids")
    public ResponseEntity<BidResponses.BidResult> participate(
            @CurrentUser Integer userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable @Min(1) Integer auctionId,
            @Valid @RequestBody BidCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(auctionCommandService.participate(userId, auctionId, request, idempotencyKey));
    }

    @GetMapping
    public AuctionResponses.CursorPage<AuctionResponses.AuctionSummary> search(
            @CurrentUser(required = false) Integer userId,
            @Valid @ModelAttribute AuctionSearchRequest request
    ) {
        return auctionQueryService.search(userId, request);
    }

    @GetMapping("/{auctionId}")
    public AuctionResponses.AuctionDetail getDetail(
            @CurrentUser(required = false) Integer userId,
            @PathVariable @Min(1) Integer auctionId
    ) {
        return auctionQueryService.getDetail(userId, auctionId);
    }

    @GetMapping("/{auctionId}/bids")
    public AuctionResponses.Page<BidResponses.BidSummary> getBids(
            @PathVariable @Min(1) Integer auctionId,
            @Valid @ModelAttribute PageRequestDto request
    ) {
        return auctionQueryService.getBids(auctionId, request);
    }

    @GetMapping("/{auctionId}/bid-context")
    public BidResponses.BidContext getBidContext(
            @CurrentUser Integer userId,
            @PathVariable @Min(1) Integer auctionId
    ) {
        return auctionQueryService.getBidContext(userId, auctionId);
    }

    @GetMapping("/mine/failed")
    public List<AuctionResponses.FailedAuctionSummary> getFailedAuctions(@CurrentUser Integer userId) {
        return auctionQueryService.getFailedAuctions(userId);
    }

}
