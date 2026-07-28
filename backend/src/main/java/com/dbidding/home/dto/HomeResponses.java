package com.dbidding.home.dto;

import java.math.BigDecimal;
import java.util.List;

public final class HomeResponses {
    private HomeResponses() {
    }

    public record Insight(
            String id,
            String title,
            long value,
            BigDecimal changeRate,
            String note,
            String sort
    ) {
    }

    public record Market(
            MarketSummary marketSummary,
            List<MarketPoint> marketHistory
    ) {
    }

    public record MarketSummary(
            long currentPriceAverage,
            BigDecimal dailyChangeRate,
            BigDecimal weeklyChangeRate,
            BigDecimal monthlyChangeRate,
            long monthlyBidCount
    ) {
    }

    public record MarketPoint(
            String date,
            long averagePrice,
            long bidCount
    ) {
    }

    public record TopGainers(
            String topGainersTitle,
            List<Ranking> topGainers
    ) {
    }

    public record Ranking(
            Integer cardId,
            String name,
            long price,
            BigDecimal changeRate,
            String theme,
            int bidCount
    ) {
    }
}
