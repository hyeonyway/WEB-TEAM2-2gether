package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.bid.RedisAuctionCreateExecutor;
import com.dbidding.auction.bid.RedisAuctionCreateResult;
import com.dbidding.auction.bid.RedisCardStateReader;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.dto.AuctionCreateRequest;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.auction.sse.AuctionStreamPublisher;
import com.dbidding.auction.port.ImageUploadPort;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.service.CardService;
import com.dbidding.wallet.service.WalletService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import com.dbidding.auction.exception.AuctionException;

@ExtendWith(MockitoExtension.class)
class AuctionRegistrationContractTest {
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private AuctionCreateWriter auctionCreateWriter;
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
                        auctionCreateWriter,
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
        org.mockito.Mockito.lenient().when(auctionRepository.findBySellerIdAndCreateIdempotencyKey(any(), anyString()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(auctionCreateWriter.save(any(Auction.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(cardService.getCardSnapshot(1)).thenReturn(card(1, "10"));
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
        verify(auctionCreateWriter).save(captor.capture(), any());
        assertThat(captor.getValue().getBuyNowPrice()).isNull();
        verify(auctionStreamPublisher).publish(any());
    }

    /**
     * uk_auctions_user_idempotency 레이스 재현: 같은 userId/idempotencyKey/요청 바디로
     * 동시에 두 번 create()가 호출되면, 사전 조회는 둘 다 "기존 레코드 없음"을 보고
     * auctionCreateWriter.save()에서 하나(패자)만 유니크 제약 위반으로 실패해야 한다.
     * 패자는 예외를 그대로 전파하지 않고, 승자가 이미 커밋해둔 레코드를 재조회해 동일한
     * 성공 응답을 반환해야 하며, 이미지 저장/이벤트 발행을 다시 수행해서는 안 된다.
     */
    @Test
    void 동시_생성_요청이_유니크_제약에_충돌해도_승자와_동일한_성공_응답을_반환한다() {
        stubDefaultImage();
        AuctionCreateRequest request = new AuctionCreateRequest(
                1, "피카츄 경매", "설명", null, null, List.of("upload-token"),
                10_000L, 1_000L, null, 12, 3_000L
        );
        ArgumentCaptor<Auction> auctionCaptor = ArgumentCaptor.forClass(Auction.class);
        when(auctionCreateWriter.save(auctionCaptor.capture(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));
        when(auctionCreateWriter.findAfterConflict(eq(1), eq("race-key")))
                .thenAnswer(invocation -> {
                    // 승자가 이미 저장을 마친 레코드를 흉내낸다: 패자가 만들려던 것과 동일한
                    // (userId, idempotencyKey, requestHash)를 갖되 DB가 채워줬을 id만 부여한다.
                    Auction winnerAuction = auctionCaptor.getValue();
                    ReflectionTestUtils.setField(winnerAuction, "id", 77);
                    return Optional.of(winnerAuction);
                });

        var response = auctionCommandService.create(1, request, "race-key");

        assertThat(response.id()).isEqualTo(77);
        verify(auctionCreateWriter).save(any(Auction.class), any());
        verify(auctionCreateWriter).findAfterConflict(1, "race-key");
        verify(auctionEventPublisher, never()).publishOpened(any());
        verify(auctionStreamPublisher, never()).publish(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    /**
     * 저장은 이미 REQUIRES_NEW로 커밋을 마친 뒤에 실행되는 부가 통지
     * (AuctionOpenedEvent 발행 / SSE 브로드캐스트)가 실패해도, 경매 자체는 이미 성공적으로
     * 생성되어 있으므로 create()는 예외를 전파하지 않고 정상 성공 응답을 반환해야 한다.
     */
    @Test
    void 경매_생성_후_SSE_발행이_실패해도_생성_응답은_정상적으로_반환된다() {
        stubDefaultImage();
        AuctionCreateRequest request = new AuctionCreateRequest(
                1, "피카츄 경매", "설명", null, null, List.of("upload-token"),
                10_000L, 1_000L, null, 12, 3_000L
        );
        org.mockito.Mockito.doThrow(new org.springframework.data.redis.RedisConnectionFailureException("redis down"))
                .when(auctionStreamPublisher).publish(any());

        var response = auctionCommandService.create(1, request, "sse-publish-failure-key");

        assertThat(response).isNotNull();
        verify(auctionCreateWriter).save(any(Auction.class), any());
        verify(auctionEventPublisher).publishOpened(any());
        verify(auctionStreamPublisher).publish(any());
    }

    /**
     * publishOpened() (위시리스트 알림 이벤트 발행) 실패에도 동일하게 예외가 전파되지 않고
     * 성공 응답을 반환해야 한다.
     */
    @Test
    void 경매_생성_후_위시리스트_알림_발행이_실패해도_생성_응답은_정상적으로_반환된다() {
        stubDefaultImage();
        AuctionCreateRequest request = new AuctionCreateRequest(
                1, "피카츄 경매", "설명", null, null, List.of("upload-token"),
                10_000L, 1_000L, null, 12, 3_000L
        );
        org.mockito.Mockito.doThrow(new RuntimeException("notification publish failed"))
                .when(auctionEventPublisher).publishOpened(any());

        var response = auctionCommandService.create(1, request, "opened-publish-failure-key");

        assertThat(response).isNotNull();
        verify(auctionCreateWriter).save(any(Auction.class), any());
    }

    @Test
    void Redis_프로필_생성은_MySQL_저장_대신_Redis_승인_ID를_즉시_반환한다() {
        stubDefaultImage();
        RedisAuctionCreateExecutor redisExecutor = mock(RedisAuctionCreateExecutor.class);
        RedisCardStateReader redisCardReader = mock(RedisCardStateReader.class);
        ReflectionTestUtils.setField(auctionCommandService, "redisAuctionCreateExecutor", redisExecutor);
        ReflectionTestUtils.setField(auctionCommandService, "redisCardStateReader", redisCardReader);
        when(redisCardReader.getCardSnapshot(1)).thenReturn(card(1, "10"));
        when(redisExecutor.execute(any())).thenReturn(new RedisAuctionCreateResult(
                42, "1720000000000-0", AuctionStatus.OPEN,
                Instant.parse("2026-08-04T00:00:00Z"), Instant.parse("2026-08-04T12:00:00Z"), false
        ));

        var response = auctionCommandService.create(1, new AuctionCreateRequest(
                1, "피카츄 경매", "설명", null, null, List.of("upload-token"),
                10_000L, 1_000L, null, 12, 3_000L
        ), "redis-create-key");

        assertThat(response.id()).isEqualTo(42);
        verify(redisExecutor).execute(any());
        verify(cardService, org.mockito.Mockito.never()).getCardSnapshot(1);
        verify(auctionCreateWriter, org.mockito.Mockito.never()).save(any(), any());
    }

    /**
     * Redis 프로필도 마찬가지로 Lua 스크립트가 ACCEPTED를 반환한 시점에 이미 경매가
     * durable하게 생성되어 있으므로(#613), 그 이후의 SSE 발행 실패가 create() 자체를
     * 실패시켜서는 안 된다.
     */
    @Test
    void Redis_프로필_생성_후_SSE_발행이_실패해도_생성_응답은_정상적으로_반환된다() {
        stubDefaultImage();
        RedisAuctionCreateExecutor redisExecutor = mock(RedisAuctionCreateExecutor.class);
        RedisCardStateReader redisCardReader = mock(RedisCardStateReader.class);
        ReflectionTestUtils.setField(auctionCommandService, "redisAuctionCreateExecutor", redisExecutor);
        ReflectionTestUtils.setField(auctionCommandService, "redisCardStateReader", redisCardReader);
        when(redisCardReader.getCardSnapshot(1)).thenReturn(card(1, "10"));
        when(redisExecutor.execute(any())).thenReturn(new RedisAuctionCreateResult(
                42, "1720000000000-0", AuctionStatus.OPEN,
                Instant.parse("2026-08-04T00:00:00Z"), Instant.parse("2026-08-04T12:00:00Z"), false
        ));
        org.mockito.Mockito.doThrow(new org.springframework.data.redis.RedisConnectionFailureException("redis down"))
                .when(auctionStreamPublisher).publish(any());

        var response = auctionCommandService.create(1, new AuctionCreateRequest(
                1, "피카츄 경매", "설명", null, null, List.of("upload-token"),
                10_000L, 1_000L, null, 12, 3_000L
        ), "redis-create-publish-failure-key");

        assertThat(response.id()).isEqualTo(42);
        verify(redisExecutor).execute(any());
        verify(auctionEventPublisher).publishOpened(any());
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
        verify(auctionCreateWriter).save(captor.capture(), any());
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
        verify(auctionCreateWriter).save(captor.capture(), any());
        assertThat(captor.getValue().getSelfGrade()).isEqualTo("민트");
    }

    @Test
    void 즉시_구매가는_첫_입찰_최소가_이상이어야_한다() {
        reset(
                auctionRepository,
                auctionCreateWriter,
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
                auctionCreateWriter,
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
                auctionCreateWriter,
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
