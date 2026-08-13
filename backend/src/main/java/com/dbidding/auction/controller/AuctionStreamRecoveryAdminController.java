package com.dbidding.auction.controller;

import com.dbidding.auction.stream.AuctionStreamRecoveryAdminService;
import com.dbidding.auction.stream.AuctionStreamRecoveryStatus;
import com.dbidding.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("redis")
@RequestMapping("/api/admin/auction-stream/recovery")
@RequiredArgsConstructor
public class AuctionStreamRecoveryAdminController {
    private final AuctionStreamRecoveryAdminService recoveryService;

    @GetMapping("/status")
    public AuctionStreamRecoveryStatus status(@CurrentUser Integer userId) {
        return recoveryService.status(userId);
    }

    @GetMapping("/events")
    public com.dbidding.auction.stream.AuctionStreamRecoveryEventPage events(
            @CurrentUser Integer userId,
            @RequestParam(defaultValue = "0") int page
    ) {
        return recoveryService.events(userId, page);
    }

    @GetMapping("/processed-events")
    public com.dbidding.auction.stream.AuctionStreamRecoveryEventPage processedEvents(
            @CurrentUser Integer userId,
            @RequestParam(defaultValue = "0") int page
    ) {
        return recoveryService.processedEvents(userId, page);
    }

    @PostMapping("/replay")
    public com.dbidding.auction.stream.AuctionStreamRecoveryReplayResponse replay(@CurrentUser Integer userId) {
        return recoveryService.replay(userId);
    }
}
