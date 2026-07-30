package com.dbidding.statistic.port;

public interface StatisticAuctionPort {
    InsightAggregate aggregateInsights();

    record InsightAggregate(
            Long totalCount,
            Long risingCount,
            Double averageRisingRate,
            Long bidAuctionCount
    ) {
    }
}
