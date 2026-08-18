package com.dbidding.dashboard.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dbidding.dashboard.domain.ParticipatingAuctionSort;
import com.dbidding.dashboard.domain.RecentWinSort;
import com.dbidding.dashboard.dto.DashboardResponse;
import com.dbidding.dashboard.service.DashboardQueryService;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.global.exception.UnauthorizedException;
import com.dbidding.global.security.CurrentUserProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardQueryService dashboardService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @Test
    void 인증_사용자의_참여중인_경매를_조회한다() throws Exception {
        given(currentUserProvider.getCurrentUserId()).willReturn(7);
        given(dashboardService.getParticipatingAuctions(
                7,
                ParticipatingAuctionSort.ENDING_SOON
        )).willReturn(List.of());

        mockMvc.perform(get("/api/dashboard/participating-auctions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void 참여중인_경매의_정렬조건을_전달한다() throws Exception {
        given(currentUserProvider.getCurrentUserId()).willReturn(7);
        given(dashboardService.getParticipatingAuctions(
                7,
                ParticipatingAuctionSort.PRICE_HIGH
        )).willReturn(List.of());

        mockMvc.perform(get("/api/dashboard/participating-auctions")
                        .queryParam("sort", "PRICE_HIGH"))
                .andExpect(status().isOk());
    }

    @Test
    void 경매_종료시각은_UTC_오프셋을_포함한다() throws Exception {
        given(currentUserProvider.getCurrentUserId()).willReturn(7);
        given(dashboardService.getParticipatingAuctions(
                7,
                ParticipatingAuctionSort.ENDING_SOON
        )).willReturn(List.of(new DashboardResponse.AuctionSnapshot(
                1,
                9,
                new DashboardResponse.CardSnapshot(1, "카드", "10", "KR", null),
                10_000L,
                12_000L,
                1_000L,
                2,
                Instant.parse("2026-07-31T03:00:00Z"),
                AuctionStatus.OPEN,
                MyBidStatus.LEADING,
                12_000L
        )));

        mockMvc.perform(get("/api/dashboard/participating-auctions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ends_at").value("2026-07-31T03:00:00Z"))
                .andExpect(jsonPath("$[0].seller_id").value(9));
    }

    @Test
    void 인증_사용자의_최근_낙찰을_조회한다() throws Exception {
        given(currentUserProvider.getCurrentUserId()).willReturn(7);
        given(dashboardService.getRecentWins(7, RecentWinSort.LATEST)).willReturn(List.of());

        mockMvc.perform(get("/api/dashboard/recent-wins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void 최근_낙찰의_정렬조건을_전달한다() throws Exception {
        given(currentUserProvider.getCurrentUserId()).willReturn(7);
        given(dashboardService.getRecentWins(7, RecentWinSort.OLDEST))
                .willReturn(List.of());

        mockMvc.perform(get("/api/dashboard/recent-wins")
                        .queryParam("sort", "OLDEST"))
                .andExpect(status().isOk());
    }

    @Test
    void 미인증_요청은_401을_반환한다() throws Exception {
        given(currentUserProvider.getCurrentUserId()).willThrow(new UnauthorizedException());

        mockMvc.perform(get("/api/dashboard/participating-auctions"))
                .andExpect(status().isUnauthorized());
    }
}
