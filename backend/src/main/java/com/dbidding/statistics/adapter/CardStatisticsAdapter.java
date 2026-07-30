package com.dbidding.statistics.adapter;

import com.dbidding.card.port.CardStatisticsPort;
import com.dbidding.statistics.domain.ItemDailyStatistic;
import com.dbidding.statistics.domain.ItemStatistic;
import com.dbidding.statistics.repository.ItemDailyStatisticRepository;
import com.dbidding.statistics.repository.ItemStatisticRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CardStatisticsAdapter implements CardStatisticsPort {
    private final ItemStatisticRepository statisticRepository;
    private final ItemDailyStatisticRepository dailyStatisticRepository;

    @Override
    public Map<Integer, Summary> getSummaries(Collection<Integer> cardIds) {
        if (cardIds.isEmpty()) {
            return Map.of();
        }
        return statisticRepository.findAllByItemIds(cardIds).stream()
                .map(this::toSummary)
                .collect(Collectors.toMap(Summary::cardId, Function.identity()));
    }

    @Override
    public Optional<Summary> getSummary(Integer cardId) {
        return statisticRepository.findById(cardId).map(this::toSummary);
    }

    @Override
    public List<DailyPrice> getDailyPrices(
            Integer cardId, LocalDate from, LocalDate to) {
        return dailyStatisticRepository
                .findByItemIdAndStatisticsDateGreaterThanEqualAndStatisticsDateLessThanOrderByStatisticsDate(
                        cardId, from, to).stream()
                .map(this::toDailyPrice)
                .toList();
    }

    private Summary toSummary(ItemStatistic statistic) {
        return new Summary(
                statistic.getItem().getId(),
                statistic.getLatestPrice(),
                statistic.getAveragePrice30d(),
                statistic.getLowestPrice30d(),
                statistic.getHighestPrice30d(),
                statistic.getBidCount30d(),
                statistic.getEndedAuctionCount30d(),
                statistic.getWishlistCount(),
                statistic.getDailyChangeRate(),
                statistic.getWeeklyChangeRate(),
                statistic.getMonthlyChangeRate()
        );
    }

    private DailyPrice toDailyPrice(ItemDailyStatistic statistic) {
        return new DailyPrice(
                statistic.getStatisticsDate(),
                statistic.getAveragePrice(),
                statistic.getBidCount()
        );
    }
}
