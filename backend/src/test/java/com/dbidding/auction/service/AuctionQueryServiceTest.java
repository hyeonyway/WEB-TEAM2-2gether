package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.dto.PageRequestDto;
import com.dbidding.auction.port.AuctionCardPort;
import com.dbidding.auction.port.CurrentUserPort;
import com.dbidding.auction.port.WalletPort;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuctionQueryServiceTest {
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private AuctionImageRepository auctionImageRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private CurrentUserPort currentUserPort;
    @Mock
    private WalletPort walletPort;
    @Mock
    private AuctionCardPort auctionCardPort;

    private AuctionQueryService auctionQueryService;

    @BeforeEach
    void setUp() {
        auctionQueryService = new AuctionQueryService(
                auctionRepository,
                auctionImageRepository,
                bidRepository,
                currentUserPort,
                walletPort,
                auctionCardPort
        );
    }

    @Test
    void 종료된_경매의_낙찰_입찰을_최고_입찰로_표시한다() {
        Auction auction = auction(AuctionStatus.ENDED);
        Bid winningBid = bid(1L, 3, auction, 45_000L, BidStatus.WON);
        when(auctionRepository.findById(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findByAuctionIdOrderByCreatedAtDescIdDesc(1, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(winningBid)));
        when(bidRepository.findFirstByAuctionIdAndStatusInOrderByBidPriceDescCreatedAtAsc(
                1,
                List.of(BidStatus.LEADING, BidStatus.WON)
        )).thenReturn(Optional.of(winningBid));

        var response = auctionQueryService.getBids(1, new PageRequestDto(0, 20));

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().isHighest()).isTrue();
    }

    @Test
    void 낙찰자의_입찰_상태를_앞선_입찰로_표시한다() {
        Auction auction = auction(AuctionStatus.ENDED);
        Bid winningBid = bid(1L, 3, auction, 45_000L, BidStatus.WON);
        when(auctionRepository.findById(1)).thenReturn(Optional.of(auction));
        when(currentUserPort.currentUser()).thenReturn(new CurrentUserPort.CurrentUser(3, "winner", true, false));
        when(walletPort.getWallet(3)).thenReturn(new WalletPort.WalletSnapshot(100_000L, 45_000L));
        when(bidRepository.findFirstByAuctionIdAndBidderIdOrderByCreatedAtDesc(1, 3))
                .thenReturn(Optional.of(winningBid));
        when(bidRepository.findByAuctionIdOrderByCreatedAtDescIdDesc(1, PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of(winningBid)));
        when(bidRepository.findFirstByAuctionIdAndStatusInOrderByBidPriceDescCreatedAtAsc(
                1,
                List.of(BidStatus.LEADING, BidStatus.WON)
        )).thenReturn(Optional.of(winningBid));

        var response = auctionQueryService.getBidContext(1);

        assertThat(response.myBidStatus()).isEqualTo(MyBidStatus.LEADING);
    }

    private Auction auction(AuctionStatus status) {
        LocalDateTime closeTime = LocalDateTime.now().minusMinutes(1);
        Auction auction = Auction.builder()
                .sellerId(2)
                .itemId(1)
                .auctionName("경매 A")
                .description("카드 상태 설명")
                .startPrice(42_000L)
                .buyNowPrice(100_000L)
                .deliveryFee(3_000L)
                .openTime(closeTime.minusHours(2))
                .estimatedCloseTime(closeTime)
                .closeTime(closeTime)
                .bidPriceUnit(1_000L)
                .hyped(false)
                .build();
        ReflectionTestUtils.setField(auction, "id", 1);
        ReflectionTestUtils.setField(auction, "status", status);
        ReflectionTestUtils.setField(auction, "currentPrice", 45_000L);
        return auction;
    }

    private Bid bid(Long id, Integer bidderId, Auction auction, Long bidPrice, BidStatus status) {
        Bid bid = new Bid(bidderId, auction, bidPrice, LocalDateTime.now().minusMinutes(5), status);
        ReflectionTestUtils.setField(bid, "id", id);
        return bid;
    }
}
