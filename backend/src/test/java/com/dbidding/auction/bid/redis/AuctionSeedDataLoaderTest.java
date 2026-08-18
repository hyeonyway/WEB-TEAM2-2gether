package com.dbidding.auction.bid.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.dbidding.auction.bid.dto.AuctionSeedDbData;
import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionImage;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuctionSeedDataLoaderTest {

    @Mock private AuctionRepository auctionRepository;
    @Mock private BidRepository bidRepository;
    @Mock private AuctionImageRepository auctionImageRepository;

    private AuctionSeedDataLoader loader;

    @BeforeEach
    void setUp() {
        loader = new AuctionSeedDataLoader(auctionRepository, bidRepository, auctionImageRepository);
    }

    @Test
    void active_경매의_seed_DB_데이터를_경매별로_묶는다() {
        Auction first = auction(101, 1, AuctionStatus.OPEN);
        Auction second = auction(102, 2, AuctionStatus.ENDING);
        Bid leading = bid(1L, first, 7, 12_000L, BidStatus.LEADING);
        Bid latest = bid(2L, second, 8, 13_000L, BidStatus.OUTBID);
        Bid recent = bid(3L, first, 9, 11_000L, BidStatus.OUTBID);
        AuctionImage image = new AuctionImage(first, "/101-front.png");
        given(auctionRepository.findByIdInAndStatusNot(List.of(101, 102), AuctionStatus.ENDED))
                .willReturn(List.of(first, second));
        given(bidRepository.findByAuctionIdInAndStatus(List.of(101, 102), BidStatus.LEADING))
                .willReturn(List.of(leading));
        given(bidRepository.findLatestBidPerBidderByAuctionIdIn(List.of(101, 102)))
                .willReturn(List.of(latest));
        given(bidRepository.findRecentFiveByAuctionIdIn(List.of(101, 102)))
                .willReturn(List.of(recent));
        given(auctionImageRepository.findByAuctionIdInOrderById(List.of(101, 102)))
                .willReturn(List.of(image));

        Map<Integer, AuctionSeedDbData> result = loader.load(List.of(101, 102));

        assertThat(result).containsOnlyKeys(101, 102);
        assertThat(result.get(101).leading()).isSameAs(leading);
        assertThat(result.get(101).imagePaths()).containsExactly("/101-front.png");
        assertThat(result.get(101).recentBids()).containsExactly(recent);
        assertThat(result.get(102).latestBids()).containsExactly(latest);
        assertThat(result.get(102).leading()).isNull();
    }

    @Test
    void ended_경매만_있으면_후속_입찰과_이미지_쿼리를_실행하지_않는다() {
        given(auctionRepository.findByIdInAndStatusNot(List.of(101), AuctionStatus.ENDED))
                .willReturn(List.of());

        Map<Integer, AuctionSeedDbData> result = loader.load(List.of(101));

        assertThat(result).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(bidRepository, auctionImageRepository);
    }

    private Auction auction(Integer id, Integer itemId, AuctionStatus status) {
        Auction auction = Auction.builder()
                .sellerId(1).itemId(itemId).auctionName("경매 " + id).description("설명")
                .startPrice(10_000L).deliveryFee(0L).openTime(Instant.parse("2026-08-13T00:00:00Z"))
                .estimatedCloseTime(Instant.parse("2026-08-14T00:00:00Z"))
                .closeTime(Instant.parse("2026-08-14T00:00:00Z")).bidPriceUnit(1_000L).build();
        ReflectionTestUtils.setField(auction, "id", id);
        ReflectionTestUtils.setField(auction, "status", status);
        return auction;
    }

    private Bid bid(Long id, Auction auction, Integer userId, long price, BidStatus status) {
        Bid bid = new Bid(userId, auction, price, Instant.parse("2026-08-13T01:00:00Z"), status);
        ReflectionTestUtils.setField(bid, "id", id);
        return bid;
    }
}
