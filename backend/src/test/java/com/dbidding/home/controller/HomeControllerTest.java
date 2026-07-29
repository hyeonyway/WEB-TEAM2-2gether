package com.dbidding.home.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dbidding.home.dto.HomeResponses;
import com.dbidding.home.service.HomeService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HomeController.class)
class HomeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HomeService homeService;

    @Test
    void 인사이트_응답_계약을_반환한다() throws Exception {
        given(homeService.getInsights()).willReturn(List.of(
                new HomeResponses.Insight(
                        "RISING", "경매가 상승", 3, new BigDecimal("12.35"),
                        "안내", "CHANGE_HIGH")
        ));

        mockMvc.perform(get("/api/home/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("RISING"))
                .andExpect(jsonPath("$[0].value").value(3))
                .andExpect(jsonPath("$[0].changeRate").value(12.35));
    }

    @Test
    void 경매_통계의_days_범위를_검증한다() throws Exception {
        mockMvc.perform(get("/api/home/market").param("days", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 상승_TOP5는_카드_식별자를_반환한다() throws Exception {
        given(homeService.getTopGainers(5)).willReturn(new HomeResponses.TopGainers(
                "전일 상승 Top 5",
                List.of(new HomeResponses.Ranking(
                        10, "피카츄", 120_000, new BigDecimal("20.00"),
                        "gold", 4, "pokemon-cards/pikachu.webp",
                        LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 26), List.of(
                                new HomeResponses.RankingPricePoint("07/26", 100_000),
                                new HomeResponses.RankingPricePoint("07/27", 120_000)
                        )))
        ));

        mockMvc.perform(get("/api/home/top-gainers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topGainers[0].cardId").value(10))
                .andExpect(jsonPath("$.topGainers[0].imageUrl").value("pokemon-cards/pikachu.webp"))
                .andExpect(jsonPath("$.topGainers[0].priceHistory[1].price").value(120000))
                .andExpect(jsonPath("$.topGainers[0].auctionId").doesNotExist());
    }

    @Test
    void 상승과_하락_TOP5를_한_응답으로_반환한다() throws Exception {
        given(homeService.getPriceMovers(5)).willReturn(
                new HomeResponses.PriceMovers(30, List.of(), List.of()));

        mockMvc.perform(get("/api/home/price-movers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodDays").value(30))
                .andExpect(jsonPath("$.gainers").isArray())
                .andExpect(jsonPath("$.losers").isArray());
    }
}
