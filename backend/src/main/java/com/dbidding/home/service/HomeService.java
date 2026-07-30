package com.dbidding.home.service;

import com.dbidding.home.dto.HomeResponses;
import com.dbidding.home.port.HomeCardPort;
import com.dbidding.home.port.HomeCardPort.CardSnapshot;
import com.dbidding.home.repository.HomeAuctionRepository;
import com.dbidding.statistics.domain.ItemDailyStatistic;
import com.dbidding.statistics.domain.MarketDailyStatistic;
import com.dbidding.statistics.repository.ItemDailyStatisticRepository;
import com.dbidding.statistics.repository.MarketDailyStatisticRepository;
import com.dbidding.statistics.repository.PriceMovementCandidate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
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
    private final ItemDailyStatisticRepository dailyStatisticRepository;
    private final MarketDailyStatisticRepository marketStatisticRepository;
    private final HomeCardPort cardPort;
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
        LocalDate fromDate = today.minusDays(days);
        Map<LocalDate, MarketDailyStatistic> aggregates =
                marketStatisticRepository
                        .findByStatisticsDateGreaterThanEqualAndStatisticsDateLessThanOrderByStatisticsDate(
                                fromDate, today).stream()
                        .collect(Collectors.toMap(
                                MarketDailyStatistic::getStatisticsDate,
                                Function.identity()
                        ));

        MarketDailyStatistic yesterdaySummary = aggregates.get(today.minusDays(1));
        long monthlyWinningPriceTotal = yesterdaySummary == null
                ? 0 : value(yesterdaySummary.getWinningPriceTotal30d());
        long monthlyEndedAuctionCount = yesterdaySummary == null
                ? 0 : value(yesterdaySummary.getEndedAuctionCount30d());
        long monthlyBidCount = yesterdaySummary == null
                ? 0 : value(yesterdaySummary.getBidCount30d());
        long monthlyHighestPrice = yesterdaySummary == null
                ? 0 : value(yesterdaySummary.getHighestPrice30d());
        List<HomeResponses.MarketPoint> history = new ArrayList<>(days);

        for (int index = 0; index < days; index++) {
            LocalDate date = fromDate.plusDays(index);
            var daily = aggregates.get(date);
            long bids = daily == null ? 0 : value(daily.getBidCount());
            history.add(new HomeResponses.MarketPoint(
                    date.format(MONTH_DAY),
                    daily == null ? null : daily.getAveragePrice(),
                    bids));
        }

        return new HomeResponses.Market(
                new HomeResponses.MarketSummary(
                        monthlyWinningPriceTotal,
                        monthlyEndedAuctionCount,
                        monthlyBidCount,
                        monthlyHighestPrice
                ),
                history
        );
    }

    public HomeResponses.PriceMovers getPriceMovers(int limit) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(30);
        var priceCandidates = dailyStatisticRepository.findPriceMovementCandidates(from, today);
        Map<Integer, CardSnapshot> cards = cardPort.getCards(
                priceCandidates.stream().map(PriceMovementCandidate::getCardId).toList());
        List<HomeResponses.Ranking> candidates = priceCandidates.stream()
                .map(candidate -> ranking(candidate, cards.get(candidate.getCardId())))
                .filter(java.util.Objects::nonNull)
                .toList();
        List<HomeResponses.Ranking> gainers = candidates.stream()
                .filter(ranking -> ranking.changeRate().signum() > 0)
                .sorted(Comparator.comparing(HomeResponses.Ranking::changeRate).reversed()
                        .thenComparing(HomeResponses.Ranking::price, Comparator.reverseOrder())
                        .thenComparing(HomeResponses.Ranking::cardId))
                .limit(limit)
                .toList();
        List<HomeResponses.Ranking> losers = candidates.stream()
                .filter(ranking -> ranking.changeRate().signum() < 0)
                .sorted(Comparator.comparing(HomeResponses.Ranking::changeRate)
                        .thenComparing(HomeResponses.Ranking::price, Comparator.reverseOrder())
                        .thenComparing(HomeResponses.Ranking::cardId))
                .limit(limit)
                .toList();
        List<Integer> itemIds = java.util.stream.Stream.concat(gainers.stream(), losers.stream())
                .map(HomeResponses.Ranking::cardId)
                .distinct()
                .toList();
        Map<Integer, List<HomeResponses.RankingPricePoint>> histories =
                itemIds.isEmpty() ? Map.of() : dailyStatisticRepository.findHistory(itemIds, from, today).stream()
                        .collect(Collectors.groupingBy(
                                ItemDailyStatistic::getItemId,
                                Collectors.collectingAndThen(Collectors.toList(), this::priceHistory)
                        ));
        return new HomeResponses.PriceMovers(
                30,
                withHistory(gainers, histories),
                withHistory(losers, histories)
        );
    }

    private HomeResponses.Ranking ranking(
            PriceMovementCandidate candidate, CardSnapshot card) {
        long currentPrice = value(candidate.getCurrentPrice());
        long previousPrice = value(candidate.getPreviousPrice());
        if (card == null || currentPrice <= 0 || previousPrice <= 0 || currentPrice == previousPrice) {
            return null;
        }
        return new HomeResponses.Ranking(
                candidate.getCardId(),
                card.name(),
                currentPrice,
                changeRate(currentPrice, previousPrice),
                card.theme(),
                candidate.getBidCount() == null ? 0 : candidate.getBidCount(),
                card.imageUrl(),
                candidate.getCurrentDate(),
                candidate.getPreviousDate(),
                List.of()
        );
    }

    private List<HomeResponses.Ranking> withHistory(
            List<HomeResponses.Ranking> rankings,
            Map<Integer, List<HomeResponses.RankingPricePoint>> histories
    ) {
        return rankings.stream()
                .map(ranking -> new HomeResponses.Ranking(
                        ranking.cardId(), ranking.name(), ranking.price(), ranking.changeRate(),
                        ranking.theme(), ranking.bidCount(), ranking.imageUrl(),
                        ranking.currentDate(), ranking.previousDate(),
                        histories.getOrDefault(ranking.cardId(), List.of())
                ))
                .toList();
    }

    private List<HomeResponses.RankingPricePoint> priceHistory(List<ItemDailyStatistic> statistics) {
        return statistics.stream()
                .sorted(Comparator.comparing(ItemDailyStatistic::getStatisticsDate))
                .map(stat -> new HomeResponses.RankingPricePoint(
                        stat.getStatisticsDate().format(MONTH_DAY),
                        price(stat)
                ))
                .filter(point -> point.price() > 0)
                .toList();
    }

    private long price(ItemDailyStatistic statistic) {
        if (statistic == null) return 0;
        if (statistic.getLatestPrice() != null) return statistic.getLatestPrice();
        return statistic.getAveragePrice() == null ? 0 : statistic.getAveragePrice();
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

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private long value(Integer value) {
        return value == null ? 0 : value.longValue();
    }
}
