package com.dbidding.auction.stream;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import com.dbidding.auction.domain.AuctionTimelineEvent;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.repository.AuctionTimelineEventRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.card.service.CardService;
import com.dbidding.order.service.OrderService;
import com.dbidding.order.repository.OrderRepository;
import com.dbidding.order.domain.Order;
import com.dbidding.order.service.redis.RedisOrderRealtimeStateProjection;
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
        ArgumentCaptor<AuctionTimelineEvent> inbox = ArgumentCaptor.forClass(AuctionTimelineEvent.class);
        verify(inboxRepository).save(inbox.capture());
        assertThat(inbox.getValue().getUserId()).isEqualTo(1);
        verify(auctionRepository, org.mockito.Mockito.never()).findByIdForUpdate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 입찰_이벤트는_입찰자_지갑을_hold하므로_inbox에_입찰자_userId를_채운다() {
        AuctionBidStreamPersistenceService service = new AuctionBidStreamPersistenceService(
                inboxRepository, auctionRepository, auctionImageRepository, bidRepository, walletService, accountRepository, walletProjectionService, orderService, orderRepository, java.util.Optional.empty(), cardService,
                auctionEventPublisher, Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC)
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

        ArgumentCaptor<AuctionTimelineEvent> inbox = ArgumentCaptor.forClass(AuctionTimelineEvent.class);
        verify(inboxRepository).save(inbox.capture());
        assertThat(inbox.getValue().getUserId()).isEqualTo(2);
        assertThat(inbox.getValue().getAuctionId()).isEqualTo(10);
    }

    @Test
    void 주문_상태_이벤트는_정산으로_지갑이_바뀔_유저의_userId를_inbox에_채운다() {
        AuctionBidStreamPersistenceService service = new AuctionBidStreamPersistenceService(
                inboxRepository, auctionRepository, auctionImageRepository, bidRepository, walletService, accountRepository, walletProjectionService, orderService, orderRepository, java.util.Optional.empty(), cardService,
                auctionEventPublisher, Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC)
        );
        given(inboxRepository.findByStreamId("order-2")).willReturn(java.util.Optional.empty());
        OrderStateChangedStreamEvent event = new OrderStateChangedStreamEvent("order-2", UUID.randomUUID(), "order.completed.v1",
                20, 10, 1L, 2, 2, 1, com.dbidding.order.domain.OrderStatus.COMPLETED, 1, 1L, 1L, 0L,
                PointTransactionType.ORDER_SETTLEMENT, 1L, "key", Instant.parse("2026-08-10T12:00:00Z"));

        service.recordPending(event);

        ArgumentCaptor<AuctionTimelineEvent> inbox = ArgumentCaptor.forClass(AuctionTimelineEvent.class);
        verify(inboxRepository).save(inbox.capture());
        assertThat(inbox.getValue().getUserId()).isEqualTo(1);
        assertThat(inbox.getValue().getAuctionId()).isEqualTo(10);
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

    @Test
    void 잘못된_schemaVersion의_malformed_이벤트은_0으로_보관한다() {
        AuctionBidStreamPersistenceService service = service(java.util.Optional.empty());
        given(inboxRepository.findByStreamId("malformed-invalid-version")).willReturn(java.util.Optional.empty());

        service.recordMalformed("malformed-invalid-version", java.util.Map.of("schemaVersion", "NaN"));

        ArgumentCaptor<com.dbidding.auction.domain.AuctionTimelineEvent> inbox = ArgumentCaptor.forClass(com.dbidding.auction.domain.AuctionTimelineEvent.class);
        verify(inboxRepository).save(inbox.capture());
        assertThat(inbox.getValue().getSchemaVersion()).isZero();
        assertThat(inbox.getValue().getEventType()).isEqualTo("unknown");
    }

    @Test
    void 첫_ERROR_이벤트를_재처리_대기열로_되돌린다() {
        AuctionBidStreamPersistenceService service = service(java.util.Optional.empty());
        com.dbidding.auction.domain.AuctionTimelineEvent event = org.mockito.Mockito.mock(com.dbidding.auction.domain.AuctionTimelineEvent.class);
        given(inboxRepository.findFirstByProjectionStatusOrderByIdAsc(com.dbidding.auction.domain.AuctionBidEventProjectionStatus.ERROR))
                .willReturn(java.util.Optional.of(event));

        assertThat(service.requeueFirstError()).isSameAs(event);

        verify(event).requeueForProjection();
    }

    @Test
    void projection_오류는_inbox에_기록하고_첫_오류임을_반환한다() {
        AuctionBidStreamPersistenceService service = service(java.util.Optional.empty());
        com.dbidding.auction.domain.AuctionTimelineEvent event = org.mockito.Mockito.mock(com.dbidding.auction.domain.AuctionTimelineEvent.class);
        given(event.getEventType()).willReturn("wallet.charged.v1");
        given(inboxRepository.findByStreamId("error-1")).willReturn(java.util.Optional.of(event));
        given(inboxRepository.existsByProjectionStatus(com.dbidding.auction.domain.AuctionBidEventProjectionStatus.ERROR)).willReturn(false);

        assertThat(service.markError("error-1", new IllegalStateException("failed"))).isTrue();

        verify(event).markError("IllegalStateException: failed");
    }

    @Test
    void projection_시도는_존재하는_inbox에만_기록한다() {
        AuctionBidStreamPersistenceService service = service(java.util.Optional.empty());
        com.dbidding.auction.domain.AuctionTimelineEvent event = org.mockito.Mockito.mock(com.dbidding.auction.domain.AuctionTimelineEvent.class);
        given(inboxRepository.findByStreamId("attempt-1")).willReturn(java.util.Optional.of(event));

        service.recordProjectionAttempt("attempt-1");

        verify(event).recordAttempt(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ENDING_전이_이벤트를_경매에_반영한다() {
        AuctionBidStreamPersistenceService service = service(java.util.Optional.empty());
        AuctionEndingStartedStreamEvent event = new AuctionEndingStartedStreamEvent("ending-1", 10,
                Instant.parse("2026-08-10T12:00:00Z"), Instant.parse("2026-08-10T11:00:00Z"));
        given(auctionRepository.findByIdForUpdate(10)).willReturn(java.util.Optional.of(auction));

        service.project(event);

        verify(auction).applyEndingTransition(event.closeTime());
    }

    @Test
    void 이미_projection된_경매_생성_이벤트는_중복_insert하지_않는다() {
        AuctionBidStreamPersistenceService service = service(java.util.Optional.empty());
        AuctionCreatedStreamEvent event = new AuctionCreatedStreamEvent("created-1", 10, 1, 3, "auction", "description",
                null, null, null, false, 10_000L, null, 3_000L, 1_000L, java.util.List.of("image"),
                Instant.parse("2026-08-10T12:00:00Z"), "key", "a".repeat(64), Instant.parse("2026-08-10T11:00:00Z"));
        given(accountRepository.existsById(1)).willReturn(true);
        given(auctionRepository.existsById(10)).willReturn(true);

        service.project(event);

        verify(auctionRepository).existsById(10);
        verify(auctionImageRepository, org.mockito.Mockito.never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 새로운_경매_생성_이벤트는_native_insert와_이미지_저장을_수행한다() throws Exception {
        AuctionBidStreamPersistenceService service = service(java.util.Optional.empty());
        jakarta.persistence.EntityManager entityManager = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        jakarta.persistence.Query query = org.mockito.Mockito.mock(jakarta.persistence.Query.class);
        java.lang.reflect.Field field = AuctionBidStreamPersistenceService.class.getDeclaredField("entityManager");
        field.setAccessible(true);
        field.set(service, entityManager);
        AuctionCreatedStreamEvent event = new AuctionCreatedStreamEvent("created-new", 11, 1, 3, "auction", "description",
                "memo", "psa", "NM", true, 10_000L, 20_000L, 3_000L, 1_000L, java.util.List.of("a", "b"),
                Instant.parse("2026-08-10T12:00:00Z"), "key", "a".repeat(64), Instant.parse("2026-08-10T11:00:00Z"));
        given(accountRepository.existsById(1)).willReturn(true);
        given(auctionRepository.existsById(11)).willReturn(false);
        given(auctionRepository.findBySellerIdAndCreateIdempotencyKey(1, "key")).willReturn(java.util.Optional.empty());
        given(cardService.getCardSnapshot(3)).willReturn(new com.dbidding.card.dto.CardResponses.CardSnapshot(3, "card", null, null, null, null));
        given(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.anyString())).willReturn(query);
        given(query.setParameter(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).willReturn(query);
        given(query.executeUpdate()).willReturn(1);
        given(entityManager.getReference(Auction.class, 11)).willReturn(auction);

        service.project(event);

        verify(query).executeUpdate();
        verify(auctionImageRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void 존재하지_않는_ORDER와_ENDING_대상은_오류로_처리한다() {
        AuctionBidStreamPersistenceService service = service(java.util.Optional.empty());
        OrderStateChangedStreamEvent orderEvent = new OrderStateChangedStreamEvent("order-missing", UUID.randomUUID(), "order.completed.v1",
                20, 10, 1L, 2, 2, 1, com.dbidding.order.domain.OrderStatus.COMPLETED, 1, 1L, 1L, 0L,
                PointTransactionType.ORDER_SETTLEMENT, 1L, "key", Instant.parse("2026-08-10T12:00:00Z"));
        given(orderRepository.findByIdForUpdate(20)).willReturn(java.util.Optional.empty());
        given(auctionRepository.findByIdForUpdate(10)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.project(orderEvent)).isInstanceOf(InvalidBidStreamEventException.class);
        assertThatThrownBy(() -> service.project(new AuctionEndingStartedStreamEvent("ending-missing", 10,
                Instant.now(), Instant.now()))).isInstanceOf(InvalidBidStreamEventException.class);
    }

    @Test
    void 주문_상태_이벤트는_주문과_지갑_및_Redis_상태를_함께_projection한다() {
        RedisOrderRealtimeStateProjection realtime = org.mockito.Mockito.mock(RedisOrderRealtimeStateProjection.class);
        AuctionBidStreamPersistenceService service = service(java.util.Optional.of(realtime));
        Order order = org.mockito.Mockito.mock(Order.class);
        given(orderRepository.findByIdForUpdate(20)).willReturn(java.util.Optional.of(order));
        given(order.getAuctionId()).willReturn(10);
        given(order.getBuyerId()).willReturn(2);
        given(order.getSellerId()).willReturn(1);
        OrderStateChangedStreamEvent event = new OrderStateChangedStreamEvent("order-1", UUID.randomUUID(), "order.completed.v1",
                20, 10, 2L, 2, 2, 1, com.dbidding.order.domain.OrderStatus.COMPLETED, 1, 3L, 50_000L, 0L,
                PointTransactionType.ORDER_SETTLEMENT, 10_000L, "order-key", Instant.parse("2026-08-10T12:00:00Z"));

        service.project(event);

        verify(order).applyProjectedStatus(com.dbidding.order.domain.OrderStatus.COMPLETED);
        verify(walletProjectionService).project(org.mockito.ArgumentMatchers.any(WalletStateChangedStreamEvent.class));
        verify(realtime).markProjectedStatusAfterCommit(10, 20, "COMPLETED");
    }

    @Test
    void 이전_입찰자가_낮은_ID이면_지갑을_release_후_hold하고_즉시낙찰은_capture한다() throws Exception {
        AuctionBidStreamPersistenceService service = service(java.util.Optional.empty());
        Bid previous = org.mockito.Mockito.mock(Bid.class);
        given(previous.getBidderId()).willReturn(1);
        BidAcceptedStreamEvent event = new BidAcceptedStreamEvent("1-0", BidStreamEventType.BUY_NOW, 10, 1L, 2,
                10_000L, 10_000L, 1, "key", "a".repeat(64), 10_000L, 1,
                Instant.parse("2026-08-10T12:00:00Z"), com.dbidding.auction.domain.AuctionStatus.ENDED,
                Instant.parse("2026-08-10T12:00:00Z"));

        invokeWalletTransition(service, event, previous, 10);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(walletService);
        order.verify(walletService).release(1, 10);
        order.verify(walletService).hold(2, 10, 10_000L);
        order.verify(walletService).capture(2, 10, 10_000L);
    }

    @Test
    void 동일한_입찰자의_즉시낙찰은_release없이_hold와_capture를_수행한다() throws Exception {
        AuctionBidStreamPersistenceService service = service(java.util.Optional.empty());
        Bid previous = org.mockito.Mockito.mock(Bid.class);
        given(previous.getBidderId()).willReturn(2);
        BidAcceptedStreamEvent event = new BidAcceptedStreamEvent("1-0", BidStreamEventType.BUY_NOW, 10, 1L, 2,
                10_000L, 10_000L, 2, "key", "a".repeat(64), 10_000L, 1,
                Instant.parse("2026-08-10T12:00:00Z"), com.dbidding.auction.domain.AuctionStatus.ENDED,
                Instant.parse("2026-08-10T12:00:00Z"));

        invokeWalletTransition(service, event, previous, 10);

        verify(walletService).hold(2, 10, 10_000L);
        verify(walletService).capture(2, 10, 10_000L);
        verify(walletService, org.mockito.Mockito.never()).release(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void ERROR로_막힌_경매가_없으면_전체_PENDING중_가장_오래된_것을_고른다() {
        AuctionBidStreamPersistenceService service = service(java.util.Optional.empty());
        AuctionTimelineEvent oldestPending = org.mockito.Mockito.mock(AuctionTimelineEvent.class);
        given(inboxRepository.findAuctionIdsWithError()).willReturn(java.util.List.of());
        given(inboxRepository.findFirstByProjectionStatusOrderByIdAsc(AuctionBidEventProjectionStatus.PENDING))
                .willReturn(java.util.Optional.of(oldestPending));

        assertThat(service.findNextEligiblePending()).contains(oldestPending);

        verify(inboxRepository, org.mockito.Mockito.never()).findEligiblePending(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ERROR로_막힌_경매가_있으면_그_경매를_제외한_PENDING만_고른다() {
        AuctionBidStreamPersistenceService service = service(java.util.Optional.empty());
        AuctionTimelineEvent eligible = org.mockito.Mockito.mock(AuctionTimelineEvent.class);
        given(inboxRepository.findAuctionIdsWithError()).willReturn(java.util.List.of(1));
        given(inboxRepository.findEligiblePending(org.mockito.ArgumentMatchers.eq(java.util.List.of(1)),
                org.mockito.ArgumentMatchers.any())).willReturn(java.util.List.of(eligible));

        assertThat(service.findNextEligiblePending()).contains(eligible);

        verify(inboxRepository, org.mockito.Mockito.never()).findFirstByProjectionStatusOrderByIdAsc(
                org.mockito.ArgumentMatchers.any());
    }

    private AuctionBidStreamPersistenceService service(java.util.Optional<RedisOrderRealtimeStateProjection> realtimeProjection) {
        return new AuctionBidStreamPersistenceService(inboxRepository, auctionRepository, auctionImageRepository, bidRepository,
                walletService, accountRepository, walletProjectionService, orderService, orderRepository, realtimeProjection,
                cardService, auctionEventPublisher, Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC));
    }

    private void invokeWalletTransition(AuctionBidStreamPersistenceService service, BidAcceptedStreamEvent event, Bid previous, int auctionId) throws Exception {
        java.lang.reflect.Method method = AuctionBidStreamPersistenceService.class.getDeclaredMethod(
                "applyWalletTransition", BidAcceptedStreamEvent.class, Bid.class, Integer.class);
        method.setAccessible(true);
        method.invoke(service, event, previous, auctionId);
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
