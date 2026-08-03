package com.dbidding.notification;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionOpenedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.notification.dto.NotificationResponse;
import com.dbidding.notification.port.CardNameFinder;
import com.dbidding.notification.port.WishlistUserFinder;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 8, 3, 12, 0);

    @Mock
    private WishlistUserFinder wishlistUserFinder;

    @Mock
    private CardNameFinder cardNameFinder;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationSseConnectionManager notificationSseConnectionManager;

    private NotificationEventListener listener;

    @Test
    void 경매가_등록되면_찜한_유저_전원에게_알림을_보내고_SSE로_push한다() {
        listener = new NotificationEventListener(wishlistUserFinder, cardNameFinder, notificationService, notificationSseConnectionManager);
        given(wishlistUserFinder.findUserIdsByCardId(10)).willReturn(List.of(1, 2, 3));
        Notification notification1 = Notification.of(1, 100, "리자몽 EX 카드의 경매가 등록되었습니다.");
        Notification notification2 = Notification.of(2, 100, "리자몽 EX 카드의 경매가 등록되었습니다.");
        Notification notification3 = Notification.of(3, 100, "리자몽 EX 카드의 경매가 등록되었습니다.");
        given(notificationService.save(1, 100, "리자몽 EX 카드의 경매가 등록되었습니다.")).willReturn(notification1);
        given(notificationService.save(2, 100, "리자몽 EX 카드의 경매가 등록되었습니다.")).willReturn(notification2);
        given(notificationService.save(3, 100, "리자몽 EX 카드의 경매가 등록되었습니다.")).willReturn(notification3);

        listener.handleAuctionOpened(openedEvent());

        verify(notificationService).save(1, 100, "리자몽 EX 카드의 경매가 등록되었습니다.");
        verify(notificationService).save(2, 100, "리자몽 EX 카드의 경매가 등록되었습니다.");
        verify(notificationService).save(3, 100, "리자몽 EX 카드의 경매가 등록되었습니다.");
        verify(notificationSseConnectionManager).push(1, NotificationResponse.from(notification1));
        verify(notificationSseConnectionManager).push(2, NotificationResponse.from(notification2));
        verify(notificationSseConnectionManager).push(3, NotificationResponse.from(notification3));
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void 찜한_유저가_없으면_알림을_보내지_않는다() {
        listener = new NotificationEventListener(wishlistUserFinder, cardNameFinder, notificationService, notificationSseConnectionManager);
        given(wishlistUserFinder.findUserIdsByCardId(10)).willReturn(List.of());

        listener.handleAuctionOpened(openedEvent());

        verifyNoInteractions(notificationService);
        verifyNoInteractions(notificationSseConnectionManager);
    }

    @Test
    void 상회_입찰이_발생하면_이전_최고_입찰자에게_금액과_함께_알림을_보내고_SSE로_push한다() {
        listener = new NotificationEventListener(wishlistUserFinder, cardNameFinder, notificationService, notificationSseConnectionManager);
        given(cardNameFinder.findNameById(10)).willReturn("리자몽 EX");
        Notification notification = Notification.of(5, 100, "리자몽 EX 카드 경매에 51,000원에 상회 입찰이 발생했습니다.");
        given(notificationService.save(5, 100, "리자몽 EX 카드 경매에 51,000원에 상회 입찰이 발생했습니다.")).willReturn(notification);

        listener.handleBidPlaced(bidPlacedEvent(5));

        verify(notificationService).save(5, 100, "리자몽 EX 카드 경매에 51,000원에 상회 입찰이 발생했습니다.");
        verify(notificationSseConnectionManager).push(5, NotificationResponse.from(notification));
    }

    @Test
    void 최초_입찰이면_상회_입찰_알림을_보내지_않는다() {
        listener = new NotificationEventListener(wishlistUserFinder, cardNameFinder, notificationService, notificationSseConnectionManager);

        listener.handleBidPlaced(bidPlacedEvent(null));

        verifyNoInteractions(cardNameFinder);
        verifyNoInteractions(notificationService);
        verifyNoInteractions(notificationSseConnectionManager);
    }

    @Test
    void 낙찰되면_낙찰자와_판매자_모두에게_알림을_보내고_SSE로_push한다() {
        listener = new NotificationEventListener(wishlistUserFinder, cardNameFinder, notificationService, notificationSseConnectionManager);
        Notification winnerNotification = Notification.of(7, 100, "리자몽 EX 카드 경매에 낙찰되었습니다.");
        Notification sellerNotification = Notification.of(9, 100, "리자몽 EX 카드 경매가 낙찰되었습니다.");
        given(notificationService.save(7, 100, "리자몽 EX 카드 경매에 낙찰되었습니다.")).willReturn(winnerNotification);
        given(notificationService.save(9, 100, "리자몽 EX 카드 경매가 낙찰되었습니다.")).willReturn(sellerNotification);

        listener.handleAuctionClosed(closedEvent(7, 50_000L));

        verify(notificationService).save(7, 100, "리자몽 EX 카드 경매에 낙찰되었습니다.");
        verify(notificationService).save(9, 100, "리자몽 EX 카드 경매가 낙찰되었습니다.");
        verify(notificationSseConnectionManager).push(7, NotificationResponse.from(winnerNotification));
        verify(notificationSseConnectionManager).push(9, NotificationResponse.from(sellerNotification));
    }

    @Test
    void 유찰되면_판매자에게만_알림을_보내고_SSE로_push한다() {
        listener = new NotificationEventListener(wishlistUserFinder, cardNameFinder, notificationService, notificationSseConnectionManager);
        Notification sellerNotification = Notification.of(9, 100, "리자몽 EX 카드 경매가 유찰되었습니다.");
        given(notificationService.save(9, 100, "리자몽 EX 카드 경매가 유찰되었습니다.")).willReturn(sellerNotification);

        listener.handleAuctionClosed(closedEvent(null, null));

        verify(notificationService).save(9, 100, "리자몽 EX 카드 경매가 유찰되었습니다.");
        verify(notificationSseConnectionManager).push(9, NotificationResponse.from(sellerNotification));
        verifyNoMoreInteractions(notificationService);
    }

    private AuctionOpenedEvent openedEvent() {
        return new AuctionOpenedEvent(
                100, 10, "리자몽 EX", "10", "JP", "/card.png", 9,
                40_000L, 40_000L, 1_000L, 0, now.plusHours(1), AuctionStatus.OPEN, 1L, now
        );
    }

    private BidPlacedEvent bidPlacedEvent(Integer previousBidderId) {
        return new BidPlacedEvent(
                100, 10, 1, previousBidderId,
                40_000L, 51_000L, 1_000L, 1, now.plusHours(1), AuctionStatus.OPEN, 2L, now
        );
    }

    private AuctionClosedEvent closedEvent(Integer winnerId, Long winningPrice) {
        return new AuctionClosedEvent(
                100, 10, "리자몽 EX", "10", "JP", "/card.png", winnerId, 9,
                40_000L, 45_000L, winningPrice, 1_000L, 3, now, AuctionStatus.ENDED, 3L, now
        );
    }
}
