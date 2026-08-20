package com.dbidding.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.service.DbAuctionQueryService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuctionRepositoryWindowQueryTest {

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Integer sellerId;
    private Integer itemId;

    @BeforeEach
    void setUp() {
        sellerId = insertUser();
        Integer cardSetId = insertCardSet();
        itemId = insertCardMetadata(cardSetId);
    }

    @Test
    void 최근_window_안에_열린_활성_경매만_조회한다() {
        Auction recentlyOpened = save(AuctionStatus.OPEN, Instant.now().minus(Duration.ofMinutes(5)), Instant.now().plus(Duration.ofHours(1)));
        Auction oldOpened = save(AuctionStatus.OPEN, Instant.now().minus(Duration.ofHours(2)), Instant.now().plus(Duration.ofHours(1)));
        save(AuctionStatus.ENDED, Instant.now().minus(Duration.ofMinutes(5)), Instant.now().minus(Duration.ofMinutes(1)));

        List<Auction> result = auctionRepository.findByStatusInAndOpenTimeGreaterThanEqual(
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING),
                Instant.now().minus(Duration.ofMinutes(10))
        );

        assertThat(result).extracting(Auction::getId).contains(recentlyOpened.getId());
        assertThat(result).extracting(Auction::getId).doesNotContain(oldOpened.getId());
    }

    @Test
    void 최근_window_안에_종료된_경매만_조회한다() {
        Auction recentlyClosed = closedAuction(AuctionStatus.ENDED, Instant.now().minus(Duration.ofMinutes(5)));
        Auction oldClosed = closedAuction(AuctionStatus.FAILED, Instant.now().minus(Duration.ofHours(2)));
        save(AuctionStatus.OPEN, Instant.now().minus(Duration.ofMinutes(5)), Instant.now().plus(Duration.ofHours(1)));

        List<Auction> result = auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(
                List.of(AuctionStatus.ENDED, AuctionStatus.FAILED),
                Instant.now().minus(Duration.ofMinutes(10))
        );

        assertThat(result).extracting(Auction::getId).contains(recentlyClosed.getId());
        assertThat(result).extracting(Auction::getId).doesNotContain(oldClosed.getId());
    }

    @Test
    void 유찰_목록_조회는_상한_개수까지만_마감_최신순으로_반환한다() {
        int cap = DbAuctionQueryService.MAX_FAILED_AUCTIONS;
        int totalFailed = cap + 5;
        Instant base = Instant.now().minus(Duration.ofDays(1));
        List<Auction> saved = new java.util.ArrayList<>();
        for (int i = 0; i < totalFailed; i++) {
            // i가 커질수록 closeTime이 더 과거 -> 마감 최신순 정렬 시 앞쪽(i가 작은 것)이 먼저 온다.
            saved.add(closedAuction(AuctionStatus.FAILED, base.minus(Duration.ofMinutes(i))));
        }

        List<Auction> result = auctionRepository.findBySellerIdAndStatusOrderByCloseTimeDesc(
                sellerId, AuctionStatus.FAILED, PageRequest.of(0, cap));

        assertThat(result).hasSize(cap);
        List<Integer> expectedIds = saved.subList(0, cap).stream().map(Auction::getId).toList();
        assertThat(result).extracting(Auction::getId).containsExactlyElementsOf(expectedIds);
    }

    private Auction save(AuctionStatus status, Instant openTime, Instant closeTime) {
        Auction auction = Auction.builder()
                .sellerId(sellerId)
                .itemId(itemId)
                .auctionName("경매")
                .description("카드 상태 설명")
                .startPrice(40_000L)
                .buyNowPrice(100_000L)
                .deliveryFee(3_000L)
                .openTime(openTime)
                .estimatedCloseTime(closeTime)
                .closeTime(closeTime)
                .bidPriceUnit(1_000L)
                .hyped(false)
                .build();
        auction = auctionRepository.save(auction);
        ReflectionTestUtils.setField(auction, "status", status);
        return auctionRepository.save(auction);
    }

    private Auction closedAuction(AuctionStatus status, Instant closeTime) {
        return save(status, closeTime.minus(Duration.ofHours(2)), closeTime);
    }

    private Integer insertUser() {
        Number generatedId = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("users")
                .usingColumns("email", "nickname", "role", "status", "encrypted_password", "salt")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(new MapSqlParameterSource()
                        .addValue("email", "auction-repo-seller@example.com")
                        .addValue("nickname", "auction-repo-seller")
                        .addValue("role", "USER")
                        .addValue("status", "ACTIVE")
                        .addValue("encrypted_password", "a".repeat(64))
                        .addValue("salt", "b".repeat(32)));
        return generatedId.intValue();
    }

    private Integer insertCardSet() {
        Number generatedId = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("card_sets")
                .usingColumns("name")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(new MapSqlParameterSource().addValue("name", "auction-repo-test-set"));
        return generatedId.intValue();
    }

    private Integer insertCardMetadata(Integer cardSetId) {
        Number generatedId = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("card_metadata")
                .usingColumns("card_set_id", "name")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(new MapSqlParameterSource()
                        .addValue("card_set_id", cardSetId)
                        .addValue("name", "auction-repo-test-card"));
        return generatedId.intValue();
    }
}
