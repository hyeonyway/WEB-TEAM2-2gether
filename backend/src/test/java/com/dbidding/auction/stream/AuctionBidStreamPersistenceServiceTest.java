package com.dbidding.auction.stream;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.repository.AuctionTimelineEventRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.card.service.CardService;
import com.dbidding.order.OrderService;
import com.dbidding.order.OrderRepository;
import com.dbidding.order.Order;
import com.dbidding.order.realtime.RedisOrderRealtimeStateProjection;
import com.dbidding.wallet.service.WalletService;
import com.dbidding.wallet.domain.PointTransactionType;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuctionBidStreamPersistenceServiceTest {
    @Mock
    private AuctionTimelineEventRepository inboxRepository;
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private AuctionImageRepository auctionImageRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private com.dbidding.wallet.service.WalletProjectionService walletProjectionService;
    @Mock
    private OrderService orderService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private RedisOrderRealtimeStateProjection orderRealtimeStateProjection;
    @Mock
    private CardService cardService;
    @Mock
    private AuctionEventPublisher auctionEventPublisher;
    @Mock
    private Auction auction;

    @Test
    void 단건_이벤트의_중복확인과_경매잠금과_최고입찰조회를_수행한다() {
        AuctionBidStreamPersistenceService service = new AuctionBidStreamPersistenceService(
                inboxRepository,
                auctionRepository,
                auctionImageRepository,
                bidRepository,
                walletService,
                accountRepository,
                walletProjectionService,
                orderService,
                orderRepository,
                java.util.Optional.empty(),
                cardService,
                auctionEventPublisher,
                Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC)
        );
        given(inboxRepository.findByStreamId("1-0")).willReturn(java.util.Optional.empty());
        given(auctionRepository.findByIdForUpdate(10)).willReturn(java.util.Optional.of(auction));
        given(auction.getId()).willReturn(10);
        given(auction.getLastBidEventVersion()).willReturn(0L);
        given(auction.isNextBidEventVersion(org.mockito.ArgumentMatchers.anyLong())).willReturn(true);
        given(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(
                10, com.dbidding.auction.domain.BidStatus.LEADING
        )).willReturn(java.util.Optional.empty());
        given(auction.applyStreamBid(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(true);

        service.persist(event("1-0", 1L, 2, null));

        // 수신 기록 생성 후 projection 완료 상태로 전이하면서 inbox를 다시 조회한다.
        verify(inboxRepository, times(2)).findByStreamId("1-0");
        verify(auctionRepository, times(1)).findByIdForUpdate(10);
        verify(bidRepository, times(1)).findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(10, com.dbidding.auction.domain.BidStatus.LEADING);
        verify(inboxRepository, times(1)).save(org.mockito.ArgumentMatchers.any());
        verify(bidRepository, times(1)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 버전이_건너뛰면_재시도_대신_경매_pause_대상이_되는_예외를_발생시킨다() {
        AuctionBidStreamPersistenceService service = new AuctionBidStreamPersistenceService(
                inboxRepository, auctionRepository, auctionImageRepository, bidRepository, walletService, accountRepository, walletProjectionService, orderService, orderRepository, java.util.Optional.empty(), cardService,
                auctionEventPublisher, Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC)
        );
        given(inboxRepository.findByStreamId("5-0")).willReturn(java.util.Optional.empty());
        given(auctionRepository.findByIdForUpdate(10)).willReturn(java.util.Optional.of(auction));
        given(auction.getId()).willReturn(10);
        given(auction.getLastBidEventVersion()).willReturn(3L);
        given(auction.isNextBidEventVersion(5L)).willReturn(false);
        given(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(10, com.dbidding.auction.domain.BidStatus.LEADING))
                .willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.persist(event("5-0", 5L, 2, null)))
                .isInstanceOf(BidStreamVersionGapException.class)
                .hasMessageContaining("auctionId=10");
    }

    @Test
    void v2_지갑_충전은_같은_타임라인_inbox에_기록하고_snapshot으로_projection한다() {
        AuctionBidStreamPersistenceService service = new AuctionBidStreamPersistenceService(
                inboxRepository, auctionRepository, auctionImageRepository, bidRepository, walletService, accountRepository, walletProjectionService, orderService, orderRepository, java.util.Optional.empty(), cardService,
                auctionEventPublisher, Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC)
        );
        WalletStateChangedStreamEvent event = new WalletStateChangedStreamEvent(
                "charge-1", UUID.randomUUID(), "wallet.charged.v1", 1, 1L, 50_000L, 0L,
                null, null, null, PointTransactionType.CHARGE, 50_000L,
                "charge-key", Instant.parse("2026-08-10T12:00:00Z")
        );
        given(inboxRepository.findByStreamId("charge-1")).willReturn(java.util.Optional.empty());

        service.persist(event);

        verify(walletProjectionService).project(event);
        verify(walletService, org.mockito.Mockito.never()).charge(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(inboxRepository).save(org.mockito.ArgumentMatchers.any());
        verify(auctionRepository, org.mockito.Mockito.never()).findByIdForUpdate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void malformed_이벤트도_원본_이벤트_타입과_스키마_버전을_inbox에_보존한다() {
        AuctionBidStreamPersistenceService service = new AuctionBidStreamPersistenceService(
                inboxRepository, auctionRepository, auctionImageRepository, bidRepository, walletService, accountRepository, walletProjectionService, orderService, orderRepository, java.util.Optional.empty(), cardService,
                auctionEventPublisher, Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC)
        );
        given(inboxRepository.findByStreamId("malformed-1")).willReturn(java.util.Optional.empty());

        service.recordMalformed("malformed-1", java.util.Map.of(
                "eventType", "wallet.charged.v1",
                "schemaVersion", "2"
        ));

        ArgumentCaptor<com.dbidding.auction.domain.AuctionTimelineEvent> inbox = ArgumentCaptor.forClass(com.dbidding.auction.domain.AuctionTimelineEvent.class);
        verify(inboxRepository).save(inbox.capture());
        assertThat(inbox.getValue().getEventType()).isEqualTo("wallet.charged.v1");
        assertThat(inbox.getValue().getSchemaVersion()).isEqualTo(2);
    }

    @Test
    void 마감_낙찰로_생성된_주문을_Redis_구매목록에_projection한다() {
        AuctionBidStreamPersistenceService service = new AuctionBidStreamPersistenceService(
                inboxRepository, auctionRepository, auctionImageRepository, bidRepository, walletService, accountRepository, walletProjectionService,
                orderService, orderRepository, java.util.Optional.of(orderRealtimeStateProjection), cardService, auctionEventPublisher, Clock.systemUTC()
        );
        AuctionCloseRequestedStreamEvent event = new AuctionCloseRequestedStreamEvent("close-1", 10, Instant.parse("2026-08-10T12:00:00.632101Z"));
        Bid winner = org.mockito.Mockito.mock(Bid.class);
        Order order = org.mockito.Mockito.mock(Order.class);
        given(auctionRepository.findByIdForUpdate(10)).willReturn(java.util.Optional.of(auction));
        given(auction.getStatus()).willReturn(com.dbidding.auction.domain.AuctionStatus.OPEN);
        given(auction.getCloseTime()).willReturn(Instant.parse("2026-08-10T12:00:00.632834Z"));
        given(auction.getId()).willReturn(10);
        given(auction.getSellerId()).willReturn(1);
        given(auction.getItemId()).willReturn(3);
        given(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(10, com.dbidding.auction.domain.BidStatus.LEADING))
                .willReturn(java.util.Optional.of(winner));
        given(winner.getBidderId()).willReturn(2);
        given(winner.getBidPrice()).willReturn(10_000L);
        given(cardService.getCardSnapshot(3)).willReturn(new com.dbidding.card.dto.CardResponses.CardSnapshot(3, "카드", null, null, null, null));
        given(orderRepository.findByAuctionId(10)).willReturn(java.util.Optional.of(order));

        service.project(event);

        verify(orderRealtimeStateProjection).markCreatedOrderAfterCommit(order, "close-1");
    }

    @Test
    void 마감_시각보다_이전_밀리초의_종료_요청은_거부한다() {
        AuctionBidStreamPersistenceService service = new AuctionBidStreamPersistenceService(
                inboxRepository, auctionRepository, auctionImageRepository, bidRepository, walletService, accountRepository, walletProjectionService,
                orderService, orderRepository, java.util.Optional.empty(), cardService, auctionEventPublisher, Clock.systemUTC()
        );
        given(auctionRepository.findByIdForUpdate(10)).willReturn(java.util.Optional.of(auction));
        given(auction.getStatus()).willReturn(com.dbidding.auction.domain.AuctionStatus.ENDING);
        given(auction.getCloseTime()).willReturn(Instant.parse("2026-08-10T12:00:00.632834Z"));

        assertThatThrownBy(() -> service.project(new AuctionCloseRequestedStreamEvent(
                "close-early", 10, Instant.parse("2026-08-10T12:00:00.631999Z")
        ))).isInstanceOf(InvalidBidStreamEventException.class)
                .hasMessageContaining("아직 종료할 수 없는 경매입니다");
    }

    private BidAcceptedStreamEvent event(String streamId, Long version, Integer bidderId, Integer previousBidderId) {
        return new BidAcceptedStreamEvent(
                streamId, BidStreamEventType.BID_ACCEPTED, 10, version, bidderId, 10_000L + version, 10_000L + version, previousBidderId,
                "request-" + version, "a".repeat(64), 10_000L + version, version.intValue(),
                Instant.parse("2027-08-11T12:00:00Z"), com.dbidding.auction.domain.AuctionStatus.OPEN,
                Instant.parse("2026-08-10T12:00:00Z")
        );
    }
}
