package com.dbidding.notification;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.dbidding.notification.event.AuctionClosedEvent;
import com.dbidding.notification.event.AuctionCreatedEvent;
import com.dbidding.notification.event.BidPlacedEvent;
import com.dbidding.notification.port.WishlistUserFinder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private WishlistUserFinder wishlistUserFinder;

    @Mock
    private NotificationService notificationService;

    private NotificationEventListener listener;

    @Test
    void 경매가_등록되면_찜한_유저_전원에게_알림을_보낸다() {
        listener = new NotificationEventListener(wishlistUserFinder, notificationService);
        given(wishlistUserFinder.findUserIdsByCardId(10)).willReturn(List.of(1, 2, 3));

        listener.handleAuctionCreated(new AuctionCreatedEvent(100, 10, "리자몽 EX", 9));

        verify(notificationService).save(1, 100, "리자몽 EX 카드의 경매가 등록되었습니다.");
        verify(notificationService).save(2, 100, "리자몽 EX 카드의 경매가 등록되었습니다.");
        verify(notificationService).save(3, 100, "리자몽 EX 카드의 경매가 등록되었습니다.");
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void 찜한_유저가_없으면_알림을_보내지_않는다() {
        listener = new NotificationEventListener(wishlistUserFinder, notificationService);
        given(wishlistUserFinder.findUserIdsByCardId(10)).willReturn(List.of());

        listener.handleAuctionCreated(new AuctionCreatedEvent(100, 10, "리자몽 EX", 9));

        verifyNoInteractions(notificationService);
    }

    @Test
    void 상회_입찰이_발생하면_이전_최고_입찰자에게_알림을_보낸다() {
        listener = new NotificationEventListener(wishlistUserFinder, notificationService);

        listener.handleBidPlaced(new BidPlacedEvent(100, 10, "리자몽 EX", 1, 5));

        verify(notificationService).save(5, 100, "리자몽 EX 카드 경매에 상회 입찰이 발생했습니다.");
    }

    @Test
    void 최초_입찰이면_상회_입찰_알림을_보내지_않는다() {
        listener = new NotificationEventListener(wishlistUserFinder, notificationService);

        listener.handleBidPlaced(new BidPlacedEvent(100, 10, "리자몽 EX", 1, null));

        verifyNoInteractions(notificationService);
    }

    @Test
    void 낙찰되면_낙찰자와_판매자_모두에게_알림을_보낸다() {
        listener = new NotificationEventListener(wishlistUserFinder, notificationService);

        listener.handleAuctionClosed(new AuctionClosedEvent(100, 10, "리자몽 EX", 7, 9, 50000L));

        verify(notificationService).save(7, 100, "리자몽 EX 카드 경매에 낙찰되었습니다.");
        verify(notificationService).save(9, 100, "리자몽 EX 카드 경매가 낙찰되었습니다.");
    }

    @Test
    void 유찰되면_판매자에게만_알림을_보낸다() {
        listener = new NotificationEventListener(wishlistUserFinder, notificationService);

        listener.handleAuctionClosed(new AuctionClosedEvent(100, 10, "리자몽 EX", null, 9, null));

        verify(notificationService).save(9, 100, "리자몽 EX 카드 경매가 유찰되었습니다.");
        verifyNoMoreInteractions(notificationService);
    }
}
