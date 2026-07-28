package com.dbidding.home.service;

import com.dbidding.card.domain.CardTheme;
import com.dbidding.card.domain.ItemStatistic;
import com.dbidding.card.repository.ItemStatisticRepository;
import com.dbidding.home.dto.HomeResponses;
import com.dbidding.home.repository.HomeAuctionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HomeService {
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM/dd");
    private static final BigDecimal ZERO_RATE = BigDecimal.ZERO.setScale(2);

    private final HomeAuctionRepository auctionRepository;
    private final ItemStatisticRepository statisticRepository;
    private final Clock clock;

    public List<HomeResponses.Insight> getInsights() {
        var aggregate = auctionRepository.aggregateInsights();
        long total = value(aggregate.getTotalCount());
        long rising = value(aggregate.getRisingCount());
        long withBids = value(aggregate.getBidAuctionCount());
        long premium = total == 0 ? 0 : (long) Math.ceil(total * 0.1);

        return List.of(
                new HomeResponses.Insight(
                        "RISING", "경매가 상승", rising,
                        rate(aggregate.getAverageRisingRate()),
                        "시작가 대비 상승률이 높은 경매부터 확인하세요.", "CHANGE_HIGH"),
                new HomeResponses.Insight(
                        "NEW_BIDS", "신규 입찰", withBids, null,
                        "입찰 수가 많은 경매부터 확인하세요.", "BID_COUNT"),
                new HomeResponses.Insight(
                        "ACTIVE", "프리미엄 경매", premium, null,
                        "현재 경매가가 높은 경매부터 확인하세요.", "PRICE_HIGH")
        );
    }

    public HomeResponses.Market getMarket(int days) {
        LocalDate today = LocalDate.now(clock);
        LocalDate fromDate = today.minusDays(days - 1L);
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        Map<LocalDate, HomeAuctionRepository.DailyMarketAggregate> aggregates =
                auctionRepository.aggregateDailyMarket(from, to).stream()
                        .collect(Collectors.toMap(
                                HomeAuctionRepository.DailyMarketAggregate::getAuctionDate,
                                Function.identity()
                        ));

        long previousPrice = auctionRepository.findPreviousDailyAverage(from)
                .map(this::rounded)
                .orElse(0L);
        long carriedPrice = previousPrice;
        long monthlyBidCount = 0;
        List<HomeResponses.MarketPoint> history = new ArrayList<>(days);

        for (int index = 0; index < days; index++) {
            LocalDate date = fromDate.plusDays(index);
            var daily = aggregates.get(date);
            long bids = daily == null ? 0 : value(daily.getBidCount());
            if (daily != null && daily.getAveragePrice() != null) {
                carriedPrice = rounded(daily.getAveragePrice());
            }
            monthlyBidCount += bids;
            history.add(new HomeResponses.MarketPoint(
                    date.format(MONTH_DAY), carriedPrice, bids));
        }

        long current = history.getLast().averagePrice();
        long dailyBase = history.size() > 1
                ? history.get(history.size() - 2).averagePrice()
                : previousPrice;
        long weeklyBase = history.size() > 7
                ? history.get(history.size() - 8).averagePrice()
                : previousPrice;
        return new HomeResponses.Market(
                new HomeResponses.MarketSummary(
                        current,
                        changeRate(current, dailyBase),
                        changeRate(current, weeklyBase),
                        changeRate(current, previousPrice),
                        monthlyBidCount
                ),
                history
        );
    }

    public HomeResponses.TopGainers getTopGainers(int limit) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime yesterdayEnd = today.atStartOfDay();
        LocalDateTime dayBeforeEnd = today.minusDays(1).atStartOfDay();

        Map<Integer, ItemStatistic> yesterday = snapshotsBefore(yesterdayEnd);
        Map<Integer, ItemStatistic> dayBefore = snapshotsBefore(dayBeforeEnd);

        List<HomeResponses.Ranking> rankings = yesterday.entrySet().stream()
                .map(entry -> ranking(entry.getValue(), dayBefore.get(entry.getKey())))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(HomeResponses.Ranking::changeRate).reversed()
                        .thenComparing(HomeResponses.Ranking::price, Comparator.reverseOrder())
                        .thenComparing(HomeResponses.Ranking::cardId))
                .limit(limit)
                .toList();

        return new HomeResponses.TopGainers("전일 상승 Top 5", rankings);
    }

    private Map<Integer, ItemStatistic> snapshotsBefore(LocalDateTime cutoff) {
        return statisticRepository.findLatestForEveryItemBefore(cutoff).stream()
                .collect(Collectors.toMap(stat -> stat.getItem().getId(), Function.identity()));
    }

    private HomeResponses.Ranking ranking(ItemStatistic current, ItemStatistic previous) {
        long currentPrice = price(current);
        long previousPrice = price(previous);
        if (currentPrice <= 0 || previousPrice <= 0 || currentPrice <= previousPrice) {
            return null;
        }
        return new HomeResponses.Ranking(
                current.getItem().getId(),
                current.getItem().getName(),
                currentPrice,
                changeRate(currentPrice, previousPrice),
                CardTheme.from(current.getItem()),
                current.getBidCount() == null ? 0 : current.getBidCount()
        );
    }

    private long price(ItemStatistic statistic) {
        if (statistic == null) return 0;
        if (statistic.getLatestPrice() != null) return statistic.getLatestPrice();
        return statistic.getAvgPrice() == null ? 0 : statistic.getAvgPrice();
    }

    private BigDecimal changeRate(long current, long previous) {
        if (previous <= 0) return ZERO_RATE;
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(Double value) {
        return value == null
                ? ZERO_RATE
                : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private long rounded(Double value) {
        return Math.round(value);
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }
}
