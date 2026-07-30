package com.dbidding.statistic.controller;

import com.dbidding.statistic.dto.StatisticResponses;
import com.dbidding.statistic.service.StatisticQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistic")
@RequiredArgsConstructor
public class StatisticController {
    private final StatisticQueryService statisticQueryService;

    @GetMapping("/insights")
    public List<StatisticResponses.Insight> getInsights() {
        return statisticQueryService.getInsights();
    }

    @GetMapping("/market")
    public StatisticResponses.Market getMarket(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return statisticQueryService.getMarket(days);
    }

    @GetMapping("/price-movers")
    public StatisticResponses.PriceMovers getPriceMovers(
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return statisticQueryService.getPriceMovers(limit);
    }
}
