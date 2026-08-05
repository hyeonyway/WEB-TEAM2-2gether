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
import com.dbidding.auction.port.AuctionCardPort;
import com.dbidding.auction.port.AuctionCardStatisticPort;
import com.dbidding.auction.port.AuctionEventPort;
import com.dbidding.auction.port.ImageUploadPort;
import com.dbidding.auction.port.WalletPort;
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
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuctionRegistrationContractTest {
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private AuctionImageRepository auctionImageRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private WalletPort walletPort;
    @Mock
    private ImageUploadPort imageUploadPort;
    @Mock
    private AuctionCardPort auctionCardPort;
    @Mock
    private AuctionCardStatisticPort auctionCardStatisticPort;
    @Mock
    private AuctionEventPort auctionEventPort;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AuctionService auctionService;

    @BeforeEach
    void setUp() {
        auctionService = new AuctionService(
                new AuctionCommandService(
                        auctionRepository,
                        auctionImageRepository,
                        bidRepository,
                        walletPort,
                        imageUploadPort,
                        auctionCardPort,
                        auctionCardStatisticPort,
                        auctionEventPort,
                        Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC),
                        eventPublisher,
                        new AuctionMetrics(new SimpleMeterRegistry())
                ),
                mock(AuctionQueryService.class),
                new AuctionMetrics(new SimpleMeterRegistry())
        );
        when(auctionRepository.findBySellerIdAndCreateIdempotencyKey(any(), anyString()))
                .thenReturn(Optional.empty());
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auctionCardPort.getCardSnapshot(1)).thenReturn(new AuctionCardPort.CardSnapshot(
                1, "피카츄", "세트", "10", "JP", "/card.png"
        ));
        when(imageUploadPort.resolveImages(List.of("upload-token"))).thenReturn(List.of(
                new ImageUploadPort.ResolvedImage("/auction.png", 0, true)
        ));
    }

    @Test
    void 즉시_구매가를_설정하지_않은_경매를_등록할_수_있다() {
        AuctionCreateRequest request = new AuctionCreateRequest(
                1, "피카츄 경매", "설명", null, null, List.of("upload-token"),
                10_000L, 1_000L, null, 12, 3_000L
        );

        auctionService.create(1, request, "registration-key");

        ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
        verify(auctionRepository).save(captor.capture());
        assertThat(captor.getValue().getBuyNowPrice()).isNull();
    }

    @Test
    void 판매자_메모와_PSA_인증번호를_경매에_저장한다() {
        AuctionCreateRequest request = new AuctionCreateRequest(
                1, "피카츄 경매", "설명", "구매자에게 전달할 메모", "12345678", List.of("upload-token"),
                10_000L, 1_000L, 20_000L, 12, 3_000L
        );

        auctionService.create(1, request, "registration-metadata-key");

        ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
        verify(auctionRepository).save(captor.capture());
        assertThat(captor.getValue().getSellerMemo()).isEqualTo("구매자에게 전달할 메모");
        assertThat(captor.getValue().getPsaCertification()).isEqualTo("12345678");
    }

    @Test
    void PSA_등급_카드는_인증번호_없이_등록할_수_없다() {
        reset(
                auctionRepository,
                auctionImageRepository,
                bidRepository,
                walletPort,
                imageUploadPort,
                auctionCardPort,
                auctionCardStatisticPort,
                auctionEventPort,
                eventPublisher
        );
        when(auctionCardPort.getCardSnapshot(1)).thenReturn(new AuctionCardPort.CardSnapshot(
                1, "피카츄", "세트", "PSA 10", "JP", "/card.png"
        ));
        AuctionCreateRequest request = new AuctionCreateRequest(
                1, "피카츄 경매", "설명", null, null, List.of("upload-token"),
                10_000L, 1_000L, null, 12, 3_000L
        );

        assertThatThrownBy(() -> auctionService.create(1, request, "psa-required-key"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PSA 인증번호");
    }

    @Test
    void PSA_인증번호는_7자리부터_10자리까지_허용한다() {
        when(auctionCardPort.getCardSnapshot(1)).thenReturn(new AuctionCardPort.CardSnapshot(
                1, "피카츄", "세트", "PSA 10", "JP", "/card.png"
        ));

        for (String certification : List.of("1234567", "1234567890")) {
            AuctionCreateRequest request = new AuctionCreateRequest(
                    1, "피카츄 경매", "설명", null, certification, List.of("upload-token"),
                    10_000L, 1_000L, null, 12, 3_000L
            );

            assertThatCode(() -> auctionService.create(1, request, "psa-" + certification))
                    .doesNotThrowAnyException();
        }
    }
}
