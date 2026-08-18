package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.dto.AuctionCursorCodec;
import com.dbidding.auction.service.dblock.DbAuctionQueryService;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.service.CardService;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.wallet.service.WalletService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
        CardService cardService = mock(CardService.class);
        WalletService walletService = mock(WalletService.class);
        DbAuctionQueryService service = new DbAuctionQueryService(
                auctionRepository,
                auctionImageRepository,
                bidRepository,
                cardService,
                new AuctionCursorCodec(),
                Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC),
                walletService
        );
        Auction auction = Auction.builder()
                .sellerId(1)
                .itemId(10)
                .auctionName("피카츄 경매")
                .description("설명")
                .sellerMemo("구매자에게 전달할 메모")
                .psaCertification("12345678")
                .selfGrade(null)
                .psaVerified(true)
                .startPrice(10_000L)
                .buyNowPrice(null)
                .deliveryFee(3_000L)
                .openTime(Instant.parse("2026-08-04T10:00:00Z"))
                .estimatedCloseTime(Instant.parse("2026-08-04T22:00:00Z"))
                .closeTime(Instant.parse("2026-08-04T22:00:00Z"))
                .bidPriceUnit(1_000L)
                .hyped(false)
                .build();
        ReflectionTestUtils.setField(auction, "id", 1);

        when(auctionRepository.findById(1)).thenReturn(Optional.of(auction));
        when(cardService.getCardSnapshot(10)).thenReturn(new CardSnapshot(
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
