package com.dbidding.home.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
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
            long monthlyWinningPriceTotal,
            long monthlyEndedAuctionCount,
            long monthlyBidCount,
            long monthlyHighestPrice
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

    public record PriceMovers(
            int periodDays,
            List<Ranking> gainers,
            List<Ranking> losers
    ) {
    }

    public record Ranking(
            Integer cardId,
            String name,
            long price,
            BigDecimal changeRate,
            String theme,
            int bidCount,
            String imageUrl,
            LocalDate currentDate,
            LocalDate previousDate,
            List<RankingPricePoint> priceHistory
    ) {
    }

    public record RankingPricePoint(
            String date,
            long price
    ) {
    }
}
