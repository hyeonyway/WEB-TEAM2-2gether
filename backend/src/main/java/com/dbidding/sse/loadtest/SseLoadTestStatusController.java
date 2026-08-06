package com.dbidding.sse.loadtest;

import com.dbidding.notification.NotificationSseConnectionManager;
import com.dbidding.auction.sse.AuctionSseConnectionManager;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test/load")
@RequiredArgsConstructor
public class SseLoadTestStatusController {
    private final AuctionSseConnectionManager auctionSse;
    private final NotificationSseConnectionManager notificationSse;

    @GetMapping("/sse-status")
    public Map<String, Object> status(@RequestParam int expected) {
        int auction = auctionSse.connectionCount();
        int notification = notificationSse.totalConnectionCount();
        return Map.of("auctionConnected", auction, "notificationConnected", notification,
                "expected", expected, "ready", auction >= expected && notification >= expected);
    }
}
