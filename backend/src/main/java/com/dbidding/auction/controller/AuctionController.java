package com.dbidding.auction.controller;

import com.dbidding.auction.dto.AuctionCreateRequest;
import com.dbidding.auction.dto.AuctionCreateResponse;
import com.dbidding.auction.dto.AuctionResponses;
import com.dbidding.auction.dto.AuctionSearchRequest;
import com.dbidding.auction.dto.BidCreateRequest;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.dto.PageRequestDto;
import com.dbidding.auction.service.AuctionService;
import com.dbidding.global.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
    private final AuctionService auctionService;

    @PostMapping
    public ResponseEntity<AuctionCreateResponse> create(
            @CurrentUser Integer userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AuctionCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(auctionService.create(userId, request, idempotencyKey));
    }

    @PostMapping("/{auctionId}/bids")
    public ResponseEntity<BidResponses.BidResult> participate(
            @CurrentUser Integer userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable @Min(1) Integer auctionId,
            @Valid @RequestBody BidCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(auctionService.participate(userId, auctionId, request, idempotencyKey));
    }

    @GetMapping
    public AuctionResponses.Page<AuctionResponses.AuctionSummary> search(
            @CurrentUser Integer userId,
            @Valid @ModelAttribute AuctionSearchRequest request
    ) {
        return auctionService.search(userId, request);
    }

    @GetMapping("/{auctionId}")
    public AuctionResponses.AuctionDetail getDetail(
            @CurrentUser Integer userId,
            @PathVariable @Min(1) Integer auctionId
    ) {
        return auctionService.getDetail(userId, auctionId);
    }

    @GetMapping("/{auctionId}/bids")
    public AuctionResponses.Page<BidResponses.BidSummary> getBids(
            @PathVariable @Min(1) Integer auctionId,
            @Valid @ModelAttribute PageRequestDto request
    ) {
        return auctionService.getBids(auctionId, request);
    }

    @GetMapping("/{auctionId}/bid-context")
    public BidResponses.BidContext getBidContext(
            @CurrentUser Integer userId,
            @PathVariable @Min(1) Integer auctionId
    ) {
        return auctionService.getBidContext(userId, auctionId);
    }

}
