package com.dbidding.card.service;

import com.dbidding.card.domain.CardMetadata;
import com.dbidding.card.domain.CardSort;
import com.dbidding.card.domain.CardTheme;
import com.dbidding.card.domain.ItemStatistic;
import com.dbidding.card.dto.CardResponses;
import com.dbidding.card.repository.CardMetadataRepository;
import com.dbidding.card.repository.ItemStatisticRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CardPriceService {
    private static final BigDecimal ZERO_RATE = BigDecimal.ZERO.setScale(2);
    private final CardMetadataRepository cardRepository;
    private final ItemStatisticRepository statisticRepository;

    public CardResponses.Page<CardResponses.CardSummary> getCards(
            String keyword, String psaGrade, CardSort sort, int page, int size) {
        var cards = cardRepository.search(keyword == null ? "" : keyword.trim(), psaGrade,
                sort.name(),
                PageRequest.of(page, size));
        var ids = cards.getContent().stream().map(CardMetadata::getId).toList();
        Map<Integer, ItemStatistic> statistics = ids.isEmpty() ? Map.of()
                : statisticRepository.findLatestByItemIds(ids).stream()
                .collect(Collectors.toMap(s -> s.getItem().getId(), Function.identity()));
        var content = cards.getContent().stream()
                .map(card -> summary(card, statistics.get(card.getId())))
                .toList();
        return new CardResponses.Page<>(content, page, size, cards.getTotalElements(), cards.hasNext());
    }

    public CardResponses.CardDetail getCard(Integer cardId, int days) {
        CardMetadata card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "카드를 찾을 수 없습니다."));
        ItemStatistic latest = statisticRepository.findFirstByItemIdOrderByStatisticsDateDesc(cardId)
                .orElse(null);
        var statistics = statisticRepository
                .findByItemIdAndStatisticsDateGreaterThanEqualOrderByStatisticsDate(
                        cardId, LocalDateTime.now().minusDays(Math.max(1, days) - 1L))
                .stream().toList();
        if (statistics.isEmpty() && latest != null) {
            statistics = List.of(latest);
        }
        var history = statistics.stream()
                .filter(stat -> stat.getAvgPrice() != null && stat.getAvgPrice() > 0)
                .map(stat -> new CardResponses.PricePoint(stat.getStatisticsDate(),
                        value(stat.getAvgPrice()), rate(stat.getDailyChangeRate()),
                        rate(stat.getWeeklyChangeRate()), rate(stat.getMonthlyChangeRate())))
                .toList();
        long marketPrice = firstPrice(
                latestNonNull(statistics, ItemStatistic::getLatestPrice),
                latestNonNull(statistics, ItemStatistic::getAvgPrice)
        );
        long averagePrice = firstPrice(
                latestNonNull(statistics, ItemStatistic::getAvgPrice),
                latestNonNull(statistics, ItemStatistic::getLatestPrice)
        );
        long lowPrice = firstPrice(
                latestNonNull(statistics, ItemStatistic::getLowestPrice),
                marketPrice
        );
        long highPrice = firstPrice(
                latestNonNull(statistics, ItemStatistic::getHighestPrice),
                marketPrice
        );
        return new CardResponses.CardDetail(
                card.getId(), card.getName(), card.getCardSet().getName(), card.getRarity(), marketPrice,
                lowPrice, highPrice, averagePrice,
                rate(latestNonNull(statistics, ItemStatistic::getDailyChangeRate)),
                rate(latestNonNull(statistics, ItemStatistic::getWeeklyChangeRate)),
                rate(latestNonNull(statistics, ItemStatistic::getMonthlyChangeRate)),
                value(latestNonNull(statistics, ItemStatistic::getBidCount)),
                value(latestNonNull(statistics, ItemStatistic::getActiveAuctionCount)),
                value(latestNonNull(statistics, ItemStatistic::getWishlistCount)),
                card.getPsaGrade(), normalizeLanguage(card.getLanguage()),
                card.getImagePath(), history);
    }

    private CardResponses.CardSummary summary(CardMetadata card, ItemStatistic stat) {
        long price = stat == null
                ? 0
                : firstPrice(stat.getLatestPrice(), stat.getAvgPrice());
        long lowPrice = stat == null ? price : firstPrice(stat.getLowestPrice(), price);
        long highPrice = stat == null ? price : firstPrice(stat.getHighestPrice(), price);
        return new CardResponses.CardSummary(card.getId(), card.getName(), price, lowPrice, highPrice,
                rate(stat == null ? null : stat.getDailyChangeRate()), CardTheme.from(card),
                stat == null ? 0 : value(stat.getBidCount()), card.getPsaGrade(),
                normalizeLanguage(card.getLanguage()), card.getImagePath());
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
    private long value(Long value) { return value == null ? 0 : value; }
    private int value(Integer value) { return value == null ? 0 : value; }

    private long firstPrice(Long... candidates) {
        return Arrays.stream(candidates)
                .filter(Objects::nonNull)
                .filter(price -> price > 0)
                .findFirst()
                .orElse(0L);
    }

    private <T> T latestNonNull(List<ItemStatistic> statistics, Function<ItemStatistic, T> getter) {
        for (int index = statistics.size() - 1; index >= 0; index--) {
            T value = getter.apply(statistics.get(index));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

}
