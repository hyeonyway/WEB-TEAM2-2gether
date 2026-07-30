package com.dbidding.card.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CardStatisticPort {
    Map<Integer, Summary> getSummaries(Collection<Integer> cardIds);

    Optional<Summary> getSummary(Integer cardId);

    List<DailyPrice> getDailyPrices(Integer cardId, LocalDate from, LocalDate to);

    record Summary(
            Integer cardId,
            Long latestPrice,
            Long averagePrice30d,
            Long lowestPrice30d,
            Long highestPrice30d,
            Integer bidCount30d,
            Integer endedAuctionCount30d,
            Integer wishlistCount,
            BigDecimal dailyChangeRate,
            BigDecimal weeklyChangeRate,
            BigDecimal monthlyChangeRate
    ) {
    }

    record DailyPrice(
            LocalDate date,
            Long averagePrice,
            Integer bidCount
    ) {
    }
}
