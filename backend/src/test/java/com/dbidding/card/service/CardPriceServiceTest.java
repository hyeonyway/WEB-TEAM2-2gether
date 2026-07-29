package com.dbidding.card.service;

import com.dbidding.card.domain.CardMetadata;
import com.dbidding.card.domain.CardSet;
import com.dbidding.card.domain.CardSort;
import com.dbidding.card.domain.ItemStatistic;
import com.dbidding.card.domain.ItemDailyStatistic;
import com.dbidding.card.repository.CardMetadataRepository;
import com.dbidding.card.repository.ItemStatisticRepository;
import com.dbidding.card.repository.ItemDailyStatisticRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
    @Autowired ItemDailyStatisticRepository dailyStatisticRepository;
    @Autowired EntityManager entityManager;

    @Test
    void 목록은_검색조건에_맞는_카드와_최신_시세를_반환한다() {
        CardSet set = new CardSet("메가 에볼루션", "ME01");
        entityManager.persist(set);
        CardMetadata pikachu = cardRepository.save(new CardMetadata(
                set, "피카츄 프로모", "JP", "10", "gold", "/pikachu.png"));
        cardRepository.save(new CardMetadata(
                set, "리자몽 프로모", "JP", "9", "multi", "/charizard.png"));
        statisticRepository.save(new ItemStatistic(pikachu, LocalDate.now().minusDays(1),
                138_000L, 130_000L, 110_000L, 149_000L, 12, 2, 20,
                new BigDecimal("2.70"), new BigDecimal("5.10"), new BigDecimal("12.10")));

        var response = cardPriceService.getCards("피카츄", "10", CardSort.PRICE, 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).singleElement().satisfies(card -> {
            assertThat(card.name()).isEqualTo("피카츄 프로모");
            assertThat(card.marketPrice()).isEqualTo(138_000L);
            assertThat(card.lowPrice()).isEqualTo(110_000L);
            assertThat(card.highPrice()).isEqualTo(149_000L);
            assertThat(card.changeRate()).isEqualByComparingTo("2.70");
            assertThat(card.bidCount()).isEqualTo(12);
        });
    }

    @Test
    void 상세는_최근_30일_통계와_요약값을_반환한다() {
        CardSet set = new CardSet("151", "SV2A");
        entityManager.persist(set);
        CardMetadata card = cardRepository.save(new CardMetadata(
                set, "피카츄 AR", "JPN", "10", "rainbow", null));
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        statisticRepository.save(new ItemStatistic(card, yesterday,
                138_000L, 127_250L, 105_000L, 155_000L, 32, 2, 30,
                new BigDecimal("2.70"), new BigDecimal("8.20"), new BigDecimal("12.10")));
        entityManager.flush();
        entityManager.createNativeQuery("""
                insert into users (
                    id, email, nickname, role, status, encrypted_password, salt
                ) values (
                    99001, 'card-price-seller@test.local', 'card-price-seller',
                    'USER', 'ACTIVE', repeat('a', 64), repeat('b', 32)
                )
                """).executeUpdate();
        entityManager.createNativeQuery("""
                insert into auctions (
                    user_id, item_id, auction_name, description,
                    start_price, current_price, buy_now_price, delivery_fee,
                    status, open_time, estimated_close_time, close_time,
                    bid_count, bid_price_unit, is_hyped, version
                ) values
                    (99001, :itemId, '진행 경매', '테스트', 1000, 1000, 2000, 0,
                     'OPEN', now(), now(), now(), 0, 1000, false, 1),
                    (99001, :itemId, '마감 임박 경매', '테스트', 1000, 1000, 2000, 0,
                     'ENDING', now(), now(), now(), 0, 1000, false, 1),
                    (99001, :itemId, '종료 경매', '테스트', 1000, 1000, 2000, 0,
                     'ENDED', now(), now(), now(), 0, 1000, false, 1)
                """).setParameter("itemId", card.getId()).executeUpdate();
        dailyStatisticRepository.save(new ItemDailyStatistic(
                card, yesterday.minusDays(10), 120_000L, 118_000L,
                115_000L, 120_000L, 12, 1));
        dailyStatisticRepository.save(new ItemDailyStatistic(
                card, yesterday, 138_000L, 138_000L,
                138_000L, 138_000L, 20, 1));

        var response = cardPriceService.getCard(card.getId(), 30);

        assertThat(response.marketPrice()).isEqualTo(138_000L);
        assertThat(response.lowPrice()).isEqualTo(105_000L);
        assertThat(response.highPrice()).isEqualTo(155_000L);
        assertThat(response.averagePrice()).isEqualTo(127_250L);
        assertThat(response.bidCount()).isEqualTo(32);
        assertThat(response.endedAuctionCount()).isEqualTo(2);
        assertThat(response.activeAuctionCount()).isEqualTo(2);
        assertThat(response.history()).hasSize(30);
        assertThat(response.history().getLast().date().toLocalDate()).isEqualTo(yesterday);
        assertThat(response.history().getFirst().bidCount()).isZero();
        assertThat(response.history().getLast().averagePrice()).isEqualTo(138_000L);
        assertThat(response.history().getLast().bidCount()).isEqualTo(20);
    }

    @Test
    void 시세가_없으면_가격을_0으로_반환한다() {
        CardSet set = new CardSet("프로모", "PROMO-FALLBACK");
        entityManager.persist(set);
        CardMetadata card = cardRepository.save(new CardMetadata(
                set, "피카츄 프로모", "JP", "10", "gold", null));
        statisticRepository.save(new ItemStatistic(card, LocalDate.now().minusDays(1),
                null, null, null, null, 0, 0, 0,
                null, null, null));

        var response = cardPriceService.getCard(card.getId(), 30);

        assertThat(response.marketPrice()).isZero();
        assertThat(response.lowPrice()).isZero();
        assertThat(response.highPrice()).isZero();
        assertThat(response.averagePrice()).isZero();
    }

    @Test
    void 가격순과_찜순으로_카드_목록을_정렬한다() {
        CardSet set = new CardSet("정렬 테스트", "SORT");
        entityManager.persist(set);
        CardMetadata expensive = cardRepository.save(new CardMetadata(
                set, "고가 카드", "JP", "10", "gold", null));
        CardMetadata popular = cardRepository.save(new CardMetadata(
                set, "인기 카드", "JP", "10", "gold", null));
        statisticRepository.save(new ItemStatistic(expensive, LocalDate.now().minusDays(1),
                500_000L, 500_000L, 480_000L, 520_000L, 1, 1, 1,
                null, null, null));
        statisticRepository.save(new ItemStatistic(popular, LocalDate.now().minusDays(1),
                100_000L, 100_000L, 90_000L, 110_000L, 1, 1, 100,
                null, null, null));
        entityManager.flush();
        entityManager.clear();

        var priceSorted = cardPriceService.getCards("", null, CardSort.PRICE, 0, 20);
        var favoriteSorted = cardPriceService.getCards("", null, CardSort.FAVORITE, 0, 20);
        var registeredSorted = cardPriceService.getCards("", null, CardSort.REGISTERED, 0, 20);

        assertThat(priceSorted.content()).extracting("name")
                .containsExactly("고가 카드", "인기 카드");
        assertThat(favoriteSorted.content()).extracting("name")
                .containsExactly("인기 카드", "고가 카드");
        assertThat(registeredSorted.content()).extracting("name")
                .containsExactly("고가 카드", "인기 카드");
    }
}
