package com.dbidding.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BidRepositoryTest {

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Integer sellerId;
    private Integer bidderId;
    private Integer otherBidderId;
    private Auction auction;

    @BeforeEach
    void setUp() {
        sellerId = insertUser("bid-repo-seller@example.com", "bid-repo-seller");
        bidderId = insertUser("bid-repo-bidder@example.com", "bid-repo-bidder");
        otherBidderId = insertUser("bid-repo-other@example.com", "bid-repo-other");
        Integer cardSetId = insertCardSet();
        Integer itemId = insertCardMetadata(cardSetId);

        auction = auctionRepository.save(Auction.builder()
                .sellerId(sellerId)
                .itemId(itemId)
                .auctionName("경매 A")
                .description("카드 상태 설명")
                .startPrice(40_000L)
                .buyNowPrice(100_000L)
                .deliveryFee(3_000L)
                .openTime(Instant.now().minus(Duration.ofHours(2)))
                .estimatedCloseTime(Instant.now().plus(Duration.ofHours(1)))
                .closeTime(Instant.now().plus(Duration.ofHours(1)))
                .bidPriceUnit(1_000L)
                .hyped(false)
                .build());
    }

    @Test
    void 낙찰된_bid를_경매와_상태로_조회한다() {
        Bid leading = bidRepository.save(Bid.leading(bidderId, auction, 45_000L, Instant.now().minus(Duration.ofMinutes(10))));
        leading.markWon();
        bidRepository.save(leading);

        Optional<Bid> result = bidRepository.findByAuctionIdAndStatus(auction.getId(), BidStatus.WON);

        assertThat(result).isPresent();
        assertThat(result.get().getBidderId()).isEqualTo(bidderId);
    }

    @Test
    void 낙찰_bid가_없으면_빈값을_반환한다() {
        Optional<Bid> result = bidRepository.findByAuctionIdAndStatus(auction.getId(), BidStatus.WON);

        assertThat(result).isEmpty();
    }

    @Test
    void LEADING_bid의_경매_id를_경매_상태와_무관하게_조회한다() {
        bidRepository.save(Bid.leading(bidderId, auction, 45_000L, Instant.now().minus(Duration.ofMinutes(10))));

        List<Integer> auctionIds = bidRepository.findAuctionIdsByStatus(BidStatus.LEADING);

        assertThat(auctionIds).contains(auction.getId());
    }

    @Test
    void LEADING이_아닌_bid의_경매_id는_조회되지_않는다() {
        Bid outbid = bidRepository.save(Bid.leading(bidderId, auction, 45_000L, Instant.now().minus(Duration.ofMinutes(10))));
        outbid.markOutbid();
        bidRepository.save(outbid);

        List<Integer> auctionIds = bidRepository.findAuctionIdsByStatus(BidStatus.LEADING);

        assertThat(auctionIds).doesNotContain(auction.getId());
    }

    @Test
    void 유저별_최신_bid만_추출한다() {
        Bid firstBid = bidRepository.save(
                Bid.leading(bidderId, auction, 41_000L, Instant.now().minus(Duration.ofMinutes(30))));
        firstBid.markOutbid();
        bidRepository.save(firstBid);

        Bid secondBid = bidRepository.save(
                Bid.leading(bidderId, auction, 43_000L, Instant.now().minus(Duration.ofMinutes(20))));
        secondBid.markOutbid();
        bidRepository.save(secondBid);

        Bid otherUserBid = bidRepository.save(
                Bid.leading(otherBidderId, auction, 45_000L, Instant.now().minus(Duration.ofMinutes(10))));

        List<Bid> latestBids = bidRepository.findLatestBidPerBidderByAuctionIdIn(List.of(auction.getId()));

        assertThat(latestBids).hasSize(2);
        Bid bidderLatest = latestBids.stream()
                .filter(bid -> bid.getBidderId().equals(bidderId))
                .findFirst()
                .orElseThrow();
        assertThat(bidderLatest.getId()).isEqualTo(secondBid.getId());
        assertThat(bidderLatest.getStatus()).isEqualTo(BidStatus.OUTBID);

        Bid otherLatest = latestBids.stream()
                .filter(bid -> bid.getBidderId().equals(otherBidderId))
                .findFirst()
                .orElseThrow();
        assertThat(otherLatest.getId()).isEqualTo(otherUserBid.getId());
        assertThat(otherLatest.getStatus()).isEqualTo(BidStatus.LEADING);
    }

    private Integer insertUser(String email, String nickname) {
        Number generatedId = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("users")
                .usingColumns("email", "nickname", "role", "status", "encrypted_password", "salt")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(new MapSqlParameterSource()
                        .addValue("email", email)
                        .addValue("nickname", nickname)
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
                .executeAndReturnKey(new MapSqlParameterSource().addValue("name", "bid-repo-test-set"));
        return generatedId.intValue();
    }

    private Integer insertCardMetadata(Integer cardSetId) {
        Number generatedId = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("card_metadata")
                .usingColumns("card_set_id", "name")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(new MapSqlParameterSource()
                        .addValue("card_set_id", cardSetId)
                        .addValue("name", "bid-repo-test-card"));
        return generatedId.intValue();
    }
}
