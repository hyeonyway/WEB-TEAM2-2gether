package com.dbidding.card.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.dbidding.card.domain.*;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.card.dto.CardResponses;
import com.dbidding.card.repository.CardMetadataRepository;
import com.dbidding.card.repository.ItemDailyStatisticRepository;
import com.dbidding.card.repository.ItemStatisticRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CardPriceService {
    private static final BigDecimal ZERO_RATE = BigDecimal.ZERO.setScale(2);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CardMetadataRepository cardRepository;
    private final ItemStatisticRepository statisticRepository;
    private final ItemDailyStatisticRepository dailyStatisticRepository;
    private final AuctionRepository auctionRepository;

    public CardResponses.Page<CardResponses.CardSummary> getCards(
            String keyword, String psaGrade, CardSort sort, int page, int size) {
        var cards = cardRepository.search(keyword == null ? "" : keyword.trim(), psaGrade,
                sort.name(), PageRequest.of(page, size));
        var ids = cards.getContent().stream().map(CardMetadata::getId).toList();
        Map<Integer, ItemStatistic> statistics = ids.isEmpty() ? Map.of()
                : statisticRepository.findAllByItemIds(ids).stream()
                .collect(Collectors.toMap(s -> s.getItem().getId(), Function.identity()));
        var content = cards.getContent().stream()
                .map(card -> summary(card, statistics.get(card.getId())))
                .toList();
        return new CardResponses.Page<>(content, page, size, cards.getTotalElements(), cards.hasNext());
    }

    public CardResponses.CardDetail getCard(Integer cardId, int days) {
        CardMetadata card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "카드를 찾을 수 없습니다."));
        ItemStatistic summary = statisticRepository.findById(cardId).orElse(null);
        LocalDate today = LocalDate.now(SEOUL);
        int range = Math.max(1, days);
        LocalDate from = today.minusDays(range);
        var daily = dailyStatisticRepository
                .findByItemIdAndStatisticsDateGreaterThanEqualAndStatisticsDateLessThanOrderByStatisticsDate(
                        cardId, from, today);
        var history = priceHistory(cardId, from, today, daily);

        long marketPrice = summary == null ? 0
                : firstPrice(summary.getLatestPrice(), summary.getAveragePrice30d());
        return new CardResponses.CardDetail(
                card.getId(), card.getName(), card.getCardSet().getName(), card.getRarity(),
                marketPrice,
                summary == null ? marketPrice : firstPrice(summary.getLowestPrice30d(), marketPrice),
                summary == null ? marketPrice : firstPrice(summary.getHighestPrice30d(), marketPrice),
                summary == null ? marketPrice : firstPrice(summary.getAveragePrice30d(), marketPrice),
                rate(summary == null ? null : summary.getDailyChangeRate()),
                rate(summary == null ? null : summary.getWeeklyChangeRate()),
                rate(summary == null ? null : summary.getMonthlyChangeRate()),
                value(summary == null ? null : summary.getBidCount30d()),
                value(summary == null ? null : summary.getEndedAuctionCount30d()),
                Math.toIntExact(auctionRepository.countByItemIdAndStatusIn(
                        cardId, List.of(AuctionStatus.OPEN, AuctionStatus.ENDING))),
                value(summary == null ? null : summary.getWishlistCount()),
                card.getPsaGrade(), normalizeLanguage(card.getLanguage()),
                card.getImagePath(), history);
    }

    private CardResponses.CardSummary summary(CardMetadata card, ItemStatistic statistic) {
        long price = statistic == null ? 0
                : firstPrice(statistic.getLatestPrice(), statistic.getAveragePrice30d());
        return new CardResponses.CardSummary(
                card.getId(), card.getName(), price,
                statistic == null ? price : firstPrice(statistic.getLowestPrice30d(), price),
                statistic == null ? price : firstPrice(statistic.getHighestPrice30d(), price),
                rate(statistic == null ? null : statistic.getDailyChangeRate()),
                CardTheme.from(card),
                value(statistic == null ? null : statistic.getBidCount30d()),
                card.getPsaGrade(), normalizeLanguage(card.getLanguage()), card.getImagePath());
    }

    private List<CardResponses.PricePoint> priceHistory(
            Integer cardId, LocalDate from, LocalDate to, List<ItemDailyStatistic> statistics) {
        Map<LocalDate, ItemDailyStatistic> byDate = statistics.stream()
                .collect(Collectors.toMap(ItemDailyStatistic::getStatisticsDate, Function.identity()));
        ItemDailyStatistic carried = dailyStatisticRepository
                .findFirstByItemIdAndStatisticsDateLessThanOrderByStatisticsDateDesc(cardId, from)
                .orElse(null);
        List<CardResponses.PricePoint> result = new ArrayList<>();
        for (LocalDate date = from; date.isBefore(to); date = date.plusDays(1)) {
            ItemDailyStatistic current = byDate.get(date);
            if (current != null) carried = current;
            long price = carried == null ? 0
                    : firstPrice(carried.getAveragePrice(), carried.getLatestPrice());
            result.add(new CardResponses.PricePoint(
                    date.atStartOfDay(), price, value(current == null ? null : current.getBidCount()),
                    ZERO_RATE, ZERO_RATE, ZERO_RATE));
        }
        return result;
    }

    private String normalizeLanguage(String language) {
        if (language == null) return "JP";
        return switch (language.toUpperCase()) {
            case "KO", "KOR", "KR", "한국어" -> "KR";
            case "EN", "ENG", "영어" -> "EN";
            default -> "JP";
        };
    }

    private BigDecimal rate(BigDecimal value) { return value == null ? ZERO_RATE : value; }
    private int value(Integer value) { return value == null ? 0 : value; }

    private long firstPrice(Long... candidates) {
        return Arrays.stream(candidates)
                .filter(Objects::nonNull)
                .filter(price -> price > 0)
                .findFirst()
                .orElse(0L);
    }
}
