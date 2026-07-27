package com.dbidding.card.service;

import com.dbidding.card.domain.CardMetadata;
import com.dbidding.card.domain.CardSet;
import com.dbidding.card.domain.CardSort;
import com.dbidding.card.domain.ItemStatistic;
import com.dbidding.card.repository.CardMetadataRepository;
import com.dbidding.card.repository.ItemStatisticRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(CardPriceService.class)
class CardPriceServiceTest {
    @Autowired CardPriceService cardPriceService;
    @Autowired CardMetadataRepository cardRepository;
    @Autowired ItemStatisticRepository statisticRepository;
    @Autowired EntityManager entityManager;

    @Test
    void 목록은_검색조건에_맞는_카드와_최신_시세를_반환한다() {
        CardSet set = new CardSet("메가 에볼루션", "ME01");
        entityManager.persist(set);
        CardMetadata pikachu = cardRepository.save(new CardMetadata(
                set, "피카츄 프로모", "JP", 10, "gold", 100_000L, "/pikachu.png"));
        cardRepository.save(new CardMetadata(
                set, "리자몽 프로모", "JP", 9, "multi", 200_000L, "/charizard.png"));
        statisticRepository.save(new ItemStatistic(pikachu, LocalDate.now().minusDays(1),
                120_000L, 115_000L, 110_000L, 125_000L, 3, 7, 1,
                new BigDecimal("1.20"), new BigDecimal("2.30"), new BigDecimal("4.50")));
        statisticRepository.save(new ItemStatistic(pikachu, LocalDate.now(),
                138_000L, 130_000L, 124_000L, 149_000L, 5, 12, 2,
                new BigDecimal("2.70"), new BigDecimal("5.10"), new BigDecimal("12.10")));

        var response = cardPriceService.getCards("피카츄", 10, CardSort.PRICE, 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).singleElement().satisfies(card -> {
            assertThat(card.name()).isEqualTo("피카츄 프로모");
            assertThat(card.marketPrice()).isEqualTo(138_000L);
            assertThat(card.changeRate()).isEqualByComparingTo("2.70");
            assertThat(card.bidCount()).isEqualTo(12);
        });
    }

    @Test
    void 상세는_최근_30일_통계와_요약값을_반환한다() {
        CardSet set = new CardSet("151", "SV2A");
        entityManager.persist(set);
        CardMetadata card = cardRepository.save(new CardMetadata(
                set, "피카츄 AR", "JPN", 10, "rainbow", 100_000L, null));
        statisticRepository.save(new ItemStatistic(card, LocalDate.now(),
                138_000L, 136_500L, 124_000L, 149_000L, 14, 20, 3,
                new BigDecimal("2.70"), new BigDecimal("8.20"), new BigDecimal("12.10")));

        var response = cardPriceService.getCard(card.getId(), 30);

        assertThat(response.marketPrice()).isEqualTo(138_000L);
        assertThat(response.lowPrice()).isEqualTo(124_000L);
        assertThat(response.activeAuctionCount()).isEqualTo(3);
        assertThat(response.history()).singleElement()
                .extracting("averagePrice", "tradeCount")
                .containsExactly(136_500L, 14);
    }

    @Test
    void 최저가와_최고가가_없으면_현재_시세를_범위로_사용한다() {
        CardSet set = new CardSet("프로모", "PROMO-FALLBACK");
        entityManager.persist(set);
        CardMetadata card = cardRepository.save(new CardMetadata(
                set, "피카츄 프로모", "JP", 10, "gold", 138_000L, null));
        statisticRepository.save(new ItemStatistic(card, LocalDate.now(),
                null, null, null, null, 0, 0, 0,
                null, null, null));

        var response = cardPriceService.getCard(card.getId(), 30);

        assertThat(response.marketPrice()).isEqualTo(138_000L);
        assertThat(response.lowPrice()).isEqualTo(124_000L);
        assertThat(response.highPrice()).isEqualTo(149_000L);
        assertThat(response.averagePrice()).isEqualTo(138_000L);
    }

    @Test
    void 가격순과_찜순으로_카드_목록을_정렬한다() {
        CardSet set = new CardSet("정렬 테스트", "SORT");
        entityManager.persist(set);
        CardMetadata expensive = cardRepository.save(new CardMetadata(
                set, "고가 카드", "JP", 10, "gold", 500_000L, null));
        CardMetadata popular = cardRepository.save(new CardMetadata(
                set, "인기 카드", "JP", 10, "gold", 100_000L, null));
        statisticRepository.save(new ItemStatistic(expensive, LocalDate.now(),
                500_000L, 500_000L, 480_000L, 520_000L, 1, 1, 0,
                null, null, null));
        statisticRepository.save(new ItemStatistic(popular, LocalDate.now(),
                100_000L, 100_000L, 90_000L, 110_000L, 1, 1, 0,
                null, null, null));
        entityManager.flush();
        entityManager.createNativeQuery(
                        "update card_metadata set favorite_count = 100 where id = :id")
                .setParameter("id", popular.getId())
                .executeUpdate();
        entityManager.clear();

        var priceSorted = cardPriceService.getCards("", null, CardSort.PRICE, 0, 20);
        var favoriteSorted = cardPriceService.getCards("", null, CardSort.FAVORITE, 0, 20);

        assertThat(priceSorted.content()).extracting("name")
                .containsExactly("고가 카드", "인기 카드");
        assertThat(favoriteSorted.content()).extracting("name")
                .containsExactly("인기 카드", "고가 카드");
    }
}
