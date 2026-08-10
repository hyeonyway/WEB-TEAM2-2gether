package com.dbidding.auction.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dbidding.auction.dto.AuctionResponses;
import com.dbidding.auction.service.AuctionCommandService;
import com.dbidding.auction.service.AuctionQueryService;
import com.dbidding.global.exception.UnauthorizedException;
import com.dbidding.global.security.CurrentUserProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuctionController.class)
class AuctionControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuctionCommandService auctionCommandService;

    @MockitoBean
    private AuctionQueryService auctionQueryService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @Test
    void 인증_사용자의_유찰_경매_목록을_조회한다() throws Exception {
        given(currentUserProvider.getCurrentUserId()).willReturn(7);
        given(auctionQueryService.getFailedAuctions(7)).willReturn(List.of(new AuctionResponses.FailedAuctionSummary(
                1, "리자몽", 42_000L, Instant.parse("2026-07-31T03:00:00Z")
        )));

        mockMvc.perform(get("/api/auctions/mine/failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].card_name").value("리자몽"))
                .andExpect(jsonPath("$[0].start_price").value(42_000))
                .andExpect(jsonPath("$[0].closed_at").value("2026-07-31T03:00:00Z"));
    }

    @Test
    void 미인증_요청은_401을_반환한다() throws Exception {
        given(currentUserProvider.getCurrentUserId()).willThrow(new UnauthorizedException());

        mockMvc.perform(get("/api/auctions/mine/failed"))
                .andExpect(status().isUnauthorized());
    }
}
