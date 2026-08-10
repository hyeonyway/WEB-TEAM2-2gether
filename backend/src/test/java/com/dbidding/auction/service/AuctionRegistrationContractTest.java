package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.dto.AuctionCreateRequest;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.auction.sse.AuctionStreamPublisher;
import com.dbidding.auction.port.ImageUploadPort;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.service.CardService;
import com.dbidding.wallet.service.WalletService;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.dbidding.auction.exception.AuctionException;

@ExtendWith(MockitoExtension.class)
class AuctionRegistrationContractTest {
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private AuctionImageRepository auctionImageRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private ImageUploadPort imageUploadPort;
    @Mock
    private CardService cardService;
    @Mock
    private AuctionEventPublisher auctionEventPublisher;
    @Mock
    private AuctionStreamPublisher auctionStreamPublisher;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AuctionCommandService auctionCommandService;

    @BeforeEach
    void setUp() {
        auctionCommandService = new AuctionCommandService(
                        auctionRepository,
                        auctionImageRepository,
                        bidRepository,
                        walletService,
                        imageUploadPort,
                        auctionEventPublisher,
                        auctionStreamPublisher,
                        cardService,
                        null,
                        Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC),
                        eventPublisher,
                        new AuctionMetrics(new SimpleMeterRegistry()),
                        null
                );
        when(auctionRepository.findBySellerIdAndCreateIdempotencyKey(any(), anyString()))
                .thenReturn(Optional.empty());
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cardService.getCardSnapshot(1)).thenReturn(card(1, "10"));
    }

    @Test
    void 즉시_구매가를_설정하지_않은_경매를_등록할_수_있다() {
        stubDefaultImage();
        AuctionCreateRequest request = new AuctionCreateRequest(
                1, "피카츄 경매", "설명", null, null, List.of("upload-token"),
                10_000L, 1_000L, null, 12, 3_000L
        );

        auctionCommandService.create(1, request, "registration-key");

        ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
        verify(auctionRepository).save(captor.capture());
        assertThat(captor.getValue().getBuyNowPrice()).isNull();
        verify(auctionStreamPublisher).publish(any());
    }

    @Test
    void 판매자_메모와_PSA_인증번호를_경매에_저장한다() {
        stubDefaultImage();
        AuctionCreateRequest request = new AuctionCreateRequest(
                1, "피카츄 경매", "설명", "구매자에게 전달할 메모", "12345678", List.of("upload-token"),
                10_000L, 1_000L, 20_000L, 12, 3_000L, "psa", null, "10"
        );

        auctionCommandService.create(1, request, "registration-metadata-key");

        ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
        verify(auctionRepository).save(captor.capture());
        assertThat(captor.getValue().getSellerMemo()).isEqualTo("구매자에게 전달할 메모");
        assertThat(captor.getValue().getPsaCertification()).isEqualTo("12345678");
    }

    @Test
    void 자체_평가_등급을_경매에_저장한다() {
        stubDefaultImage();
        AuctionCreateRequest request = new AuctionCreateRequest(
                1, "피카츄 경매", "설명", null, null, List.of("upload-token"),
                10_000L, 1_000L, null, 12, 3_000L, "self", "민트", null
        );

        auctionCommandService.create(1, request, "self-grade-key");

        ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
        verify(auctionRepository).save(captor.capture());
        assertThat(captor.getValue().getSelfGrade()).isEqualTo("민트");
    }

    @Test
    void 즉시_구매가는_첫_입찰_최소가_이상이어야_한다() {
        reset(
                auctionRepository,
                auctionImageRepository,
                bidRepository,
                walletService,
                cardService,
                auctionEventPublisher,
                eventPublisher
        );
        AuctionCreateRequest request = new AuctionCreateRequest(
                1, "피카츄 경매", "설명", null, null, List.of("upload-token"),
                10_000L, 5_000L, 11_000L, 12, 3_000L, "self", "민트", null
        );

        assertThatThrownBy(() -> auctionCommandService.create(1, request, "buy-now-range-key"))
				.isInstanceOf(AuctionException.class)
                .hasMessageContaining("호가 단위");
    }

    @Test
    void PSA_등급_카드는_인증번호_없이_등록할_수_없다() {
        reset(
                auctionRepository,
                auctionImageRepository,
                bidRepository,
                walletService,
                cardService,
                auctionEventPublisher,
                eventPublisher
        );
        when(cardService.getCardSnapshot(1)).thenReturn(card(1, "PSA 10"));
        AuctionCreateRequest request = new AuctionCreateRequest(
                1, "피카츄 경매", "설명", null, null, List.of("upload-token"),
                10_000L, 1_000L, null, 12, 3_000L, "psa", null, "10"
        );

        assertThatThrownBy(() -> auctionCommandService.create(1, request, "psa-required-key"))
				.isInstanceOf(AuctionException.class)
                .hasMessageContaining("PSA 인증번호");
    }

    @Test
    void PSA_인증번호는_7자리부터_10자리까지_허용한다() {
        stubDefaultImage();
        when(cardService.getCardSnapshot(1)).thenReturn(card(1, "PSA 10"));

        for (String certification : List.of("1234567", "1234567890")) {
            AuctionCreateRequest request = new AuctionCreateRequest(
                    1, "피카츄 경매", "설명", null, certification, List.of("upload-token"),
                    10_000L, 1_000L, null, 12, 3_000L, "psa", null, "10"
            );

            assertThatCode(() -> auctionCommandService.create(1, request, "psa-" + certification))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void PSA_인증_결과_등급이_선택한_카드와_다르면_등록할_수_없다() {
        reset(
                auctionRepository,
                auctionImageRepository,
                bidRepository,
                walletService,
                cardService,
                auctionEventPublisher,
                eventPublisher
        );
        when(cardService.getCardSnapshot(1)).thenReturn(card(1, "PSA 10"));
        AuctionCreateRequest request = new AuctionCreateRequest(
                1, "피카츄 경매", "설명", null, "12345678", List.of("upload-token"),
                10_000L, 1_000L, null, 12, 3_000L, "psa", null, "8"
        );

        assertThatThrownBy(() -> auctionCommandService.create(1, request, "psa-grade-mismatch-key"))
				.isInstanceOf(AuctionException.class)
                .hasMessageContaining("등급이 일치하지 않습니다");
    }

    private CardSnapshot card(Integer itemId, String psaGrade) {
        return new CardSnapshot(itemId, "피카츄", "세트", psaGrade, "JP", "/card.png");
    }

    private void stubDefaultImage() {
        when(imageUploadPort.resolveImages(List.of("upload-token"))).thenReturn(List.of(
                new ImageUploadPort.ResolvedImage("/auction.png", 0, true)
        ));
    }
}
