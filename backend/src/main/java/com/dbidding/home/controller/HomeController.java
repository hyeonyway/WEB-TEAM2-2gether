package com.dbidding.home.controller;

import com.dbidding.home.dto.HomeResponses;
import com.dbidding.home.service.HomeService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {
    private final HomeService homeService;

    @GetMapping("/insights")
    public List<HomeResponses.Insight> getInsights() {
        return homeService.getInsights();
    }

    @GetMapping("/market")
    public HomeResponses.Market getMarket(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return homeService.getMarket(days);
    }

    @GetMapping("/top-gainers")
    public HomeResponses.TopGainers getTopGainers(
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return homeService.getTopGainers(limit);
    }

    @GetMapping("/price-movers")
    public HomeResponses.PriceMovers getPriceMovers(
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return homeService.getPriceMovers(limit);
    }
}
