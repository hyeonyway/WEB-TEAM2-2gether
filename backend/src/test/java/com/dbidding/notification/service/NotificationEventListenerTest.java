package com.dbidding.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionOpenedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.card.dto.CardResponses;
import com.dbidding.card.service.CardPriceService;
import com.dbidding.notification.domain.Notification;
import com.dbidding.notification.domain.NotificationType;
import com.dbidding.notification.dto.NotificationInsertRow;
import com.dbidding.notification.dto.NotificationResponse;
import com.dbidding.notification.repository.NotificationRepository;
import com.dbidding.notification.sse.NotificationPushMessage;
import com.dbidding.notification.sse.NotificationPushPublisher;
import com.dbidding.order.event.OrderCancelledEvent;
import com.dbidding.order.event.OrderCompletedEvent;
import com.dbidding.wishlist.service.WishlistService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    private final Instant now = Instant.parse("2026-08-03T12:00:00Z");

    @Mock
    private WishlistService wishlistService;

    @Mock
    private CardPriceService cardPriceService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPushPublisher notificationPushPublisher;

    private NotificationEventListener listener;

    @Test
    void 경매가_등록되면_찜한_유저_전원에게_동일한_알림_객체로_SSE를_push한다() {
        listener = new NotificationEventListener(wishlistService, cardPriceService, notificationService, notificationRepository, notificationPushPublisher);
        given(wishlistService.findUserIdsByCardId(10)).willReturn(List.of(1, 2, 3));

        listener.handleAuctionOpened(openedEvent());

        verify(notificationService).saveAllIgnoringDuplicates(
                List.of(1, 2, 3), 100, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."
        );
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationPushMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationPushPublisher).publish(messagesCaptor.capture());
        List<NotificationPushMessage> messages = messagesCaptor.getValue();
        assertThat(messages).extracting(NotificationPushMessage::userId).containsExactly(1, 2, 3);
        NotificationResponse sharedPayload = messages.get(0).payload();
        assertThat(messages).extracting(NotificationPushMessage::payload).containsOnly(sharedPayload);
        assertNotification(sharedPayload, 100, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다.");
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void 찜한_유저가_없으면_알림을_보내지_않는다() {
        listener = new NotificationEventListener(wishlistService, cardPriceService, notificationService, notificationRepository, notificationPushPublisher);
        given(wishlistService.findUserIdsByCardId(10)).willReturn(List.of());

        listener.handleAuctionOpened(openedEvent());

        verifyNoInteractions(notificationService);
        verifyNoInteractions(notificationPushPublisher);
    }

    @Test
    void 상회_입찰이_발생하면_이전_최고_입찰자에게_금액과_함께_알림을_보내고_SSE로_push한다() {
        listener = new NotificationEventListener(wishlistService, cardPriceService, notificationService, notificationRepository, notificationPushPublisher);
        given(cardPriceService.getCard(10, 1)).willReturn(cardDetail("리자몽 EX"));
        Notification notification = Notification.ofBid(5, 100, NotificationType.OUTBID, 5L, "리자몽 EX 카드 경매에 51,000원에 상회 입찰이 발생했습니다.");
        given(notificationService.saveForBid(5, 100, NotificationType.OUTBID, 5L, "리자몽 EX 카드 경매에 51,000원에 상회 입찰이 발생했습니다.")).willReturn(notification);

        listener.handleBidPlaced(bidPlacedEvent(5));

        verify(notificationService).saveForBid(5, 100, NotificationType.OUTBID, 5L, "리자몽 EX 카드 경매에 51,000원에 상회 입찰이 발생했습니다.");
        verify(notificationPushPublisher).publish(5, NotificationResponse.from(notification));
    }

    @Test
    void 복구_배치와_레이스로_저장이_중복_실패해도_기존_알림을_찾아_SSE로_push한다() {
        listener = new NotificationEventListener(wishlistService, cardPriceService, notificationService, notificationRepository, notificationPushPublisher);
        given(cardPriceService.getCard(10, 1)).willReturn(cardDetail("리자몽 EX"));
        given(notificationService.saveForBid(5, 100, NotificationType.OUTBID, 5L, "리자몽 EX 카드 경매에 51,000원에 상회 입찰이 발생했습니다."))
                .willThrow(new DataIntegrityViolationException("duplicate"));
        Notification alreadySaved = Notification.ofBid(5, 100, NotificationType.OUTBID, 5L, "리자몽 EX 카드 경매에 51,000원에 상회 입찰이 발생했습니다.");
        given(notificationRepository.findByUserIdAndAuctionIdAndTypeAndBidId(5, 100, NotificationType.OUTBID, 5L))
                .willReturn(Optional.of(alreadySaved));

        listener.handleBidPlaced(bidPlacedEvent(5));

        verify(notificationPushPublisher).publish(5, NotificationResponse.from(alreadySaved));
    }

    @Test
    void 최초_입찰이면_상회_입찰_알림을_보내지_않는다() {
        listener = new NotificationEventListener(wishlistService, cardPriceService, notificationService, notificationRepository, notificationPushPublisher);

        listener.handleBidPlaced(bidPlacedEvent(null));

        verifyNoInteractions(cardPriceService);
        verifyNoInteractions(notificationService);
        verifyNoInteractions(notificationPushPublisher);
    }

    @Test
    void 낙찰되면_낙찰자와_판매자_알림을_한번에_저장하고_각각_SSE로_push한다() {
        listener = new NotificationEventListener(wishlistService, cardPriceService, notificationService, notificationRepository, notificationPushPublisher);

        listener.handleAuctionClosed(closedEvent(7, 50_000L));

        verify(notificationService).insertAllIgnoringDuplicates(List.of(
                NotificationInsertRow.of(7, 100, NotificationType.AUCTION_WON, "리자몽 EX 카드 경매에 낙찰되었습니다."),
                NotificationInsertRow.of(9, 100, NotificationType.AUCTION_WON, "리자몽 EX 카드 경매가 낙찰되었습니다.")
        ));
        ArgumentCaptor<NotificationResponse> winnerPayload = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(notificationPushPublisher).publish(eq(7), winnerPayload.capture());
        assertNotification(winnerPayload.getValue(), 100, NotificationType.AUCTION_WON, "리자몽 EX 카드 경매에 낙찰되었습니다.");
        ArgumentCaptor<NotificationResponse> sellerPayload = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(notificationPushPublisher).publish(eq(9), sellerPayload.capture());
        assertNotification(sellerPayload.getValue(), 100, NotificationType.AUCTION_WON, "리자몽 EX 카드 경매가 낙찰되었습니다.");
    }

    @Test
    void 유찰되면_판매자_알림만_저장하고_SSE로_push한다() {
        listener = new NotificationEventListener(wishlistService, cardPriceService, notificationService, notificationRepository, notificationPushPublisher);

        listener.handleAuctionClosed(closedEvent(null, null));

        verify(notificationService).insertAllIgnoringDuplicates(List.of(
                NotificationInsertRow.of(9, 100, NotificationType.AUCTION_UNSOLD, "리자몽 EX 카드 경매가 유찰되었습니다.")
        ));
        ArgumentCaptor<NotificationResponse> sellerPayload = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(notificationPushPublisher).publish(eq(9), sellerPayload.capture());
        assertNotification(sellerPayload.getValue(), 100, NotificationType.AUCTION_UNSOLD, "리자몽 EX 카드 경매가 유찰되었습니다.");
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void 주문이_완료되면_구매자와_판매자_알림을_한번에_저장하고_각각_SSE로_push한다() {
        listener = new NotificationEventListener(wishlistService, cardPriceService, notificationService, notificationRepository, notificationPushPublisher);

        listener.handleOrderCompleted(new OrderCompletedEvent(1, 100, 7, 9, "리자몽 EX"));

        verify(notificationService).insertAllIgnoringDuplicates(List.of(
                NotificationInsertRow.of(7, 100, NotificationType.ORDER_COMPLETED, "리자몽 EX 카드 구매가 확정되었습니다."),
                NotificationInsertRow.of(9, 100, NotificationType.ORDER_COMPLETED, "리자몽 EX 카드 판매 대금이 정산되었습니다.")
        ));
        ArgumentCaptor<NotificationResponse> buyerPayload = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(notificationPushPublisher).publish(eq(7), buyerPayload.capture());
        assertNotification(buyerPayload.getValue(), 100, NotificationType.ORDER_COMPLETED, "리자몽 EX 카드 구매가 확정되었습니다.");
        ArgumentCaptor<NotificationResponse> sellerPayload = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(notificationPushPublisher).publish(eq(9), sellerPayload.capture());
        assertNotification(sellerPayload.getValue(), 100, NotificationType.ORDER_COMPLETED, "리자몽 EX 카드 판매 대금이 정산되었습니다.");
    }

    @Test
    void 구매자가_취소하면_구매자에게는_환불_판매자에게는_구매자가_취소했다는_알림을_보낸다() {
        listener = new NotificationEventListener(wishlistService, cardPriceService, notificationService, notificationRepository, notificationPushPublisher);

        listener.handleOrderCancelled(new OrderCancelledEvent(1, 100, 7, 9, "리자몽 EX", OrderCancelledEvent.CancelledBy.BUYER));

        verify(notificationService).insertAllIgnoringDuplicates(List.of(
                NotificationInsertRow.of(7, 100, NotificationType.ORDER_CANCELLED, "리자몽 EX 카드 구매가 취소되어 환불되었습니다."),
                NotificationInsertRow.of(9, 100, NotificationType.ORDER_CANCELLED, "구매자가 리자몽 EX 카드 거래를 취소했습니다.")
        ));
        ArgumentCaptor<NotificationResponse> buyerPayload = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(notificationPushPublisher).publish(eq(7), buyerPayload.capture());
        assertNotification(buyerPayload.getValue(), 100, NotificationType.ORDER_CANCELLED, "리자몽 EX 카드 구매가 취소되어 환불되었습니다.");
        ArgumentCaptor<NotificationResponse> sellerPayload = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(notificationPushPublisher).publish(eq(9), sellerPayload.capture());
        assertNotification(sellerPayload.getValue(), 100, NotificationType.ORDER_CANCELLED, "구매자가 리자몽 EX 카드 거래를 취소했습니다.");
    }

    @Test
    void 판매자가_취소하면_판매자에게는_판매취소_구매자에게는_환불_알림을_보낸다() {
        listener = new NotificationEventListener(wishlistService, cardPriceService, notificationService, notificationRepository, notificationPushPublisher);

        listener.handleOrderCancelled(new OrderCancelledEvent(1, 100, 7, 9, "리자몽 EX", OrderCancelledEvent.CancelledBy.SELLER));

        verify(notificationService).insertAllIgnoringDuplicates(List.of(
                NotificationInsertRow.of(7, 100, NotificationType.ORDER_CANCELLED, "판매자가 리자몽 EX 카드 거래를 취소하여 환불되었습니다."),
                NotificationInsertRow.of(9, 100, NotificationType.ORDER_CANCELLED, "리자몽 EX 카드 판매를 취소했습니다.")
        ));
        ArgumentCaptor<NotificationResponse> buyerPayload = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(notificationPushPublisher).publish(eq(7), buyerPayload.capture());
        assertNotification(buyerPayload.getValue(), 100, NotificationType.ORDER_CANCELLED, "판매자가 리자몽 EX 카드 거래를 취소하여 환불되었습니다.");
        ArgumentCaptor<NotificationResponse> sellerPayload = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(notificationPushPublisher).publish(eq(9), sellerPayload.capture());
        assertNotification(sellerPayload.getValue(), 100, NotificationType.ORDER_CANCELLED, "리자몽 EX 카드 판매를 취소했습니다.");
    }

    private void assertNotification(NotificationResponse response, Integer auctionId, NotificationType type, String message) {
        assertThat(response.auctionId()).isEqualTo(auctionId);
        assertThat(response.type()).isEqualTo(type);
        assertThat(response.bidId()).isEqualTo(Notification.NO_BID);
        assertThat(response.message()).isEqualTo(message);
        assertThat(response.isRead()).isFalse();
    }

    private AuctionOpenedEvent openedEvent() {
        return new AuctionOpenedEvent(
                100, 10, "리자몽 EX", "10", "JP", "/card.png", 9,
                40_000L, 40_000L, 1_000L, 0, now.plus(Duration.ofHours(1)), AuctionStatus.OPEN, now
        );
    }

    private BidPlacedEvent bidPlacedEvent(Integer previousBidderId) {
        return new BidPlacedEvent(
                100, 10, 1, previousBidderId, previousBidderId == null ? null : 5L,
                40_000L, 51_000L, 1_000L, 1, now.plus(Duration.ofHours(1)), AuctionStatus.OPEN, now
        );
    }

    private AuctionClosedEvent closedEvent(Integer winnerId, Long winningPrice) {
        return new AuctionClosedEvent(
                100, 10, "리자몽 EX", "10", "JP", "/card.png", winnerId, 9,
                40_000L, 45_000L, winningPrice, 1_000L, 3, now, AuctionStatus.ENDED, now
        );
    }

    private CardResponses.CardDetail cardDetail(String name) {
        return new CardResponses.CardDetail(
                10, name, null, null, 0, 0, 0, 0, null, null, null,
                0, 0, 0, 0, null, null, null, List.of()
        );
    }
}
