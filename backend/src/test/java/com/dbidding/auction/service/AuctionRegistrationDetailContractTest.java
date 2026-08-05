package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.dto.AuctionCursorCodec;
import com.dbidding.auction.port.AuctionCardPort;
import com.dbidding.auction.port.WalletPort;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuctionRegistrationDetailContractTest {

    @Test
    void 상세_조회는_등록한_판매자_메모와_PSA_인증번호를_반환한다() {
        AuctionRepository auctionRepository = mock(AuctionRepository.class);
        AuctionImageRepository auctionImageRepository = mock(AuctionImageRepository.class);
        BidRepository bidRepository = mock(BidRepository.class);
        AuctionCardPort auctionCardPort = mock(AuctionCardPort.class);
        AuctionQueryService service = new AuctionQueryService(
                auctionRepository,
                auctionImageRepository,
                bidRepository,
                mock(WalletPort.class),
                auctionCardPort,
                new AuctionCursorCodec()
        );
        Auction auction = Auction.builder()
                .sellerId(1)
                .itemId(10)
                .auctionName("피카츄 경매")
                .description("설명")
                .sellerMemo("구매자에게 전달할 메모")
                .psaCertification("12345678")
                .startPrice(10_000L)
                .buyNowPrice(null)
                .deliveryFee(3_000L)
                .openTime(LocalDateTime.of(2026, 8, 4, 10, 0))
                .estimatedCloseTime(LocalDateTime.of(2026, 8, 4, 22, 0))
                .closeTime(LocalDateTime.of(2026, 8, 4, 22, 0))
                .bidPriceUnit(1_000L)
                .hyped(false)
                .build();
        ReflectionTestUtils.setField(auction, "id", 1);

        when(auctionRepository.findById(1)).thenReturn(Optional.of(auction));
        when(auctionCardPort.getCardSnapshot(10)).thenReturn(new AuctionCardPort.CardSnapshot(
                10, "피카츄", "세트", "PSA 10", "JP", "/card.png"
        ));
        when(auctionImageRepository.findByAuctionIdOrderById(1)).thenReturn(List.of());

        var response = service.getDetail(null, 1);

        assertThat(response.sellerMemo()).isEqualTo("구매자에게 전달할 메모");
        assertThat(response.psaCertification().certificationNumber()).isEqualTo("12345678");
        assertThat(response.psaCertification().verified()).isTrue();
        assertThat(response.buyNowPrice()).isNull();
    }
}
