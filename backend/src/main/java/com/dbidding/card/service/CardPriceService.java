package com.dbidding.card.service;

import com.dbidding.card.domain.CardMetadata;
import com.dbidding.card.domain.CardSort;
import com.dbidding.card.domain.ItemStatistic;
import com.dbidding.card.dto.CardResponses;
import com.dbidding.card.repository.CardMetadataRepository;
import com.dbidding.card.repository.ItemStatisticRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
            String keyword, Integer psaGrade, CardSort sort, int page, int size) {
        var cards = cardRepository.search(keyword == null ? "" : keyword.trim(), psaGrade,
                sort.name(),
                PageRequest.of(page, size));
        var ids = cards.getContent().stream().map(CardMetadata::getId).toList();
        Map<Long, ItemStatistic> statistics = ids.isEmpty() ? Map.of()
                : statisticRepository.findLatestByItemIds(ids).stream()
                .collect(Collectors.toMap(s -> s.getItem().getId(), Function.identity()));
        var content = cards.getContent().stream()
                .map(card -> summary(card, statistics.get(card.getId())))
                .toList();
        return new CardResponses.Page<>(content, page, size, cards.getTotalElements(), cards.hasNext());
    }

    public CardResponses.CardDetail getCard(Long cardId, int days) {
        CardMetadata card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "카드를 찾을 수 없습니다."));
        ItemStatistic latest = statisticRepository.findFirstByItemIdOrderByStatisticsDateDesc(cardId)
                .orElse(null);
        var history = statisticRepository
                .findByItemIdAndStatisticsDateGreaterThanEqualOrderByStatisticsDate(
                        cardId, LocalDate.now().minusDays(Math.max(1, days) - 1L))
                .stream()
                .map(stat -> new CardResponses.PricePoint(stat.getStatisticsDate(),
                        value(stat.getAvgPrice()), value(stat.getTradeCount())))
                .toList();
        long referencePrice = value(card.getReferencePrice());
        long marketPrice = latest == null
                ? referencePrice
                : firstPrice(latest.getLatestPrice(), latest.getAvgPrice(), card.getReferencePrice());
        long averagePrice = latest == null
                ? marketPrice
                : firstPrice(latest.getAvgPrice(), latest.getLatestPrice(), card.getReferencePrice());
        long lowPrice = latest == null
                ? estimatedLow(marketPrice)
                : firstPrice(latest.getLowestPrice(), estimatedLow(marketPrice));
        long highPrice = latest == null
                ? estimatedHigh(marketPrice)
                : firstPrice(latest.getHighestPrice(), estimatedHigh(marketPrice));
        return new CardResponses.CardDetail(
                card.getId(), card.getName(), card.getCardSet().getName(), card.getRarity(), marketPrice,
                lowPrice, highPrice, averagePrice,
                rate(latest == null ? null : latest.getDailyChangeRate()),
                rate(latest == null ? null : latest.getWeeklyChangeRate()),
                rate(latest == null ? null : latest.getMonthlyChangeRate()),
                latest == null ? 0 : value(latest.getTradeCount()),
                latest == null ? 0 : value(latest.getBidCount()),
                latest == null ? 0 : value(latest.getActiveAuctionCount()),
                value(card.getFavoriteCount()), card.getPsaGrade(), normalizeLanguage(card.getLanguage()),
                card.getImagePath(), history);
    }

    private CardResponses.CardSummary summary(CardMetadata card, ItemStatistic stat) {
        long price = stat == null
                ? value(card.getReferencePrice())
                : firstPrice(stat.getLatestPrice(), stat.getAvgPrice(), card.getReferencePrice());
        return new CardResponses.CardSummary(card.getId(), card.getName(), price,
                rate(stat == null ? null : stat.getDailyChangeRate()), theme(card),
                stat == null ? 0 : value(stat.getBidCount()), card.getPsaGrade(),
                normalizeLanguage(card.getLanguage()), card.getImagePath());
    }

    private String theme(CardMetadata card) {
        String rarity = card.getRarity() == null ? "" : card.getRarity().toLowerCase();
        if (rarity.contains("water")) return "water";
        if (rarity.contains("dark")) return "dark";
        if (rarity.contains("sketch")) return "sketch";
        if (rarity.contains("multi") || rarity.contains("rainbow")) return "multi";
        return "gold";
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

    private long firstPrice(Long candidate, long fallback) {
        return candidate != null && candidate > 0 ? candidate : fallback;
    }

    private long estimatedLow(long marketPrice) {
        return Math.round(marketPrice * 0.9 / 1_000.0) * 1_000L;
    }

    private long estimatedHigh(long marketPrice) {
        return Math.round(marketPrice * 1.08 / 1_000.0) * 1_000L;
    }
}
