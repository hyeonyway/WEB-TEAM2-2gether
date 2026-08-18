package com.dbidding.card.service;

import com.dbidding.card.domain.*;
import com.dbidding.card.exception.CardException;
import com.dbidding.card.dto.CardResponses;
import com.dbidding.card.port.CardAuctionPort;
import com.dbidding.card.repository.CardMetadataRepository;
import com.dbidding.statistic.service.StatisticQueryService;
import com.dbidding.statistic.service.StatisticQueryService.CardSummary;
import com.dbidding.statistic.service.StatisticQueryService.DailyPrice;
import com.dbidding.wishlist.service.WishlistService;
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

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CardPriceService {
    private static final BigDecimal ZERO_RATE = BigDecimal.ZERO.setScale(2);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CardMetadataRepository cardRepository;
    private final StatisticQueryService statisticQueryService;
    private final CardAuctionPort auctionPort;
    private final WishlistService wishlistService;

    public CardResponses.Page<CardResponses.CardSummary> getCards(
            String keyword, String psaGrade, CardSort sort, int page, int size) {
        var cards = cardRepository.search(keyword == null ? "" : keyword.trim(), psaGrade,
                sort.name(), PageRequest.of(page, size));
        var ids = cards.getContent().stream().map(CardMetadata::getId).toList();
        Map<Integer, CardSummary> statistics = statisticQueryService.getCardSummaries(ids);
        var content = cards.getContent().stream()
                .map(card -> summary(card, statistics.get(card.getId())))
                .toList();
        return new CardResponses.Page<>(content, page, size, cards.getTotalElements(), cards.hasNext());
    }

    public List<CardResponses.CardSummary> getWishlistedCards(Integer userId) {
        var orderedIds = wishlistService.findCardIdsByUserId(userId).stream().distinct().toList();
        var cardsById = cardRepository.findAllById(orderedIds).stream()
                .collect(Collectors.toMap(CardMetadata::getId, Function.identity()));
        Map<Integer, CardSummary> statistics = statisticQueryService.getCardSummaries(orderedIds);
        return orderedIds.stream()
                .map(cardsById::get)
                .filter(Objects::nonNull)
                .map(card -> summary(card, statistics.get(card.getId())))
                .toList();
    }

    public CardResponses.CardDetail getCard(Integer cardId, int days) {
        CardMetadata card = cardRepository.findById(cardId)
                .orElseThrow(CardException::notFound);
        CardSummary summary = statisticQueryService.getCardSummary(cardId).orElse(null);
        LocalDate today = LocalDate.now(SEOUL);
        int range = Math.max(1, days);
        LocalDate from = today.minusDays(range);
        var daily = statisticQueryService.getDailyPrices(cardId, from, today);
        var history = priceHistory(from, today, daily);

        long marketPrice = summary == null ? 0
                : firstPrice(summary.latestPrice(), summary.averagePrice30d());
        return new CardResponses.CardDetail(
                card.getId(), card.getName(), card.getCardSet().getName(), card.getRarity(),
                marketPrice,
                summary == null ? marketPrice : firstPrice(summary.lowestPrice30d(), marketPrice),
                summary == null ? marketPrice : firstPrice(summary.highestPrice30d(), marketPrice),
                summary == null ? marketPrice : firstPrice(summary.averagePrice30d(), marketPrice),
                rate(summary == null ? null : summary.dailyChangeRate()),
                rate(summary == null ? null : summary.weeklyChangeRate()),
                rate(summary == null ? null : summary.monthlyChangeRate()),
                value(summary == null ? null : summary.bidCount30d()),
                value(summary == null ? null : summary.endedAuctionCount30d()),
                auctionPort.countActiveAuctions(cardId),
                wishlistService.countWishlists(cardId),
                card.getPsaGrade(), normalizeLanguage(card.getLanguage()),
                card.getImagePath(), history);
    }

    private CardResponses.CardSummary summary(CardMetadata card, CardSummary statistic) {
        long price = statistic == null ? 0
                : firstPrice(statistic.latestPrice(), statistic.averagePrice30d());
        return new CardResponses.CardSummary(
                card.getId(), card.getName(), card.getCardSet().getName(), price,
                statistic == null ? price : firstPrice(statistic.lowestPrice30d(), price),
                statistic == null ? price : firstPrice(statistic.highestPrice30d(), price),
                rate(statistic == null ? null : statistic.dailyChangeRate()),
                CardTheme.from(card),
                value(statistic == null ? null : statistic.bidCount30d()),
                card.getPsaGrade(), normalizeLanguage(card.getLanguage()), card.getImagePath());
    }

    private List<CardResponses.PricePoint> priceHistory(
            LocalDate from, LocalDate to, List<DailyPrice> statistics) {
        Map<LocalDate, DailyPrice> byDate = statistics.stream()
                .collect(Collectors.toMap(DailyPrice::date, Function.identity()));
        List<CardResponses.PricePoint> result = new ArrayList<>();
        for (LocalDate date = from; date.isBefore(to); date = date.plusDays(1)) {
            DailyPrice current = byDate.get(date);
            Long price = current == null ? null : current.averagePrice();
            result.add(new CardResponses.PricePoint(
                    date, price,
                    value(current == null ? null : current.endedAuctionCount()),
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
