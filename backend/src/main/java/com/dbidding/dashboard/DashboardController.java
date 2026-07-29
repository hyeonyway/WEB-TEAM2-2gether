package com.dbidding.dashboard;

import com.dbidding.dashboard.dto.DashboardResponse;
import com.dbidding.global.security.CurrentUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/participating-auctions")
    public List<DashboardResponse.AuctionSnapshot> getParticipatingAuctions(
            @CurrentUser Integer userId,
            @RequestParam(defaultValue = "ENDING_SOON") ParticipatingAuctionSort sort
    ) {
        return dashboardService.getParticipatingAuctions(userId, sort);
    }

    @GetMapping("/recent-wins")
    public List<DashboardResponse.AuctionSnapshot> getRecentWins(
            @CurrentUser Integer userId,
            @RequestParam(defaultValue = "LATEST") RecentWinSort sort
    ) {
        return dashboardService.getRecentWins(userId, sort);
    }
}
