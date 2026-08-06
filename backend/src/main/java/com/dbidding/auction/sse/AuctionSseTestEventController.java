package com.dbidding.auction.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("test")
@RequestMapping("/api/auctions/stream/test-events")
@RequiredArgsConstructor
public class AuctionSseTestEventController {
    private final AuctionSseTestBidApplicationService bidApplicationService;

    @PostMapping("/random-bid")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AuctionStreamPayload publishRandomBid() {
        return bidApplicationService.publishRandomBid();
    }
}
