package com.dbidding.notification;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionCreatedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.notification.port.WishlistUserFinder;
import java.time.LocalDateTime;
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

        listener.handleAuctionCreated(createdEvent());

        verify(notificationService).save(1, 100, "리자몽 EX 카드의 경매가 등록되었습니다.");
        verify(notificationService).save(2, 100, "리자몽 EX 카드의 경매가 등록되었습니다.");
        verify(notificationService).save(3, 100, "리자몽 EX 카드의 경매가 등록되었습니다.");
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void 찜한_유저가_없으면_알림을_보내지_않는다() {
        listener = new NotificationEventListener(wishlistUserFinder, notificationService);
        given(wishlistUserFinder.findUserIdsByCardId(10)).willReturn(List.of());

        listener.handleAuctionCreated(createdEvent());

        verifyNoInteractions(notificationService);
    }

    @Test
    void 상회_입찰이_발생하면_이전_최고_입찰자에게_알림을_보낸다() {
        listener = new NotificationEventListener(wishlistUserFinder, notificationService);

        listener.handleBidPlaced(bidPlacedEvent(5));

        verify(notificationService).save(5, 100, "리자몽 EX 카드 경매에 상회 입찰이 발생했습니다.");
    }

    @Test
    void 낙찰되면_낙찰자와_판매자_모두에게_알림을_보낸다() {
        listener = new NotificationEventListener(wishlistUserFinder, notificationService);

        listener.handleAuctionClosed(closedEvent(7));

        verify(notificationService).save(7, 100, "리자몽 EX 카드 경매에 낙찰되었습니다.");
        verify(notificationService).save(9, 100, "리자몽 EX 카드 경매가 낙찰되었습니다.");
    }

    @Test
    void 유찰되면_판매자에게만_알림을_보낸다() {
        listener = new NotificationEventListener(wishlistUserFinder, notificationService);

        listener.handleAuctionClosed(closedEvent(null));

        verify(notificationService).save(9, 100, "리자몽 EX 카드 경매가 유찰되었습니다.");
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void 최초_입찰이면_상회_입찰_알림을_보내지_않는다() {
        listener = new NotificationEventListener(wishlistUserFinder, notificationService);

        listener.handleBidPlaced(bidPlacedEvent(null));

        verifyNoInteractions(notificationService);
    }

    private AuctionCreatedEvent createdEvent() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 30, 12, 0);
        return new AuctionCreatedEvent(
                100, 10, "리자몽 EX", "10", "JP", "/card.png", 9,
                40_000L, 40_000L, 1_000L, 0, now.plusHours(1),
                AuctionStatus.OPEN, 1L, now
        );
    }

    private BidPlacedEvent bidPlacedEvent(Integer previousBidderId) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 30, 12, 0);
        return new BidPlacedEvent(
                100, 10, "리자몽 EX", "10", "JP", "/card.png", 9,
                7, previousBidderId, 40_000L, 50_000L, 50_000L, 1_000L,
                2, now.plusHours(1), AuctionStatus.OPEN, 2L, now
        );
    }

    private AuctionClosedEvent closedEvent(Integer winnerId) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 30, 13, 0);
        return new AuctionClosedEvent(
                100, 10, "리자몽 EX", "10", "JP", "/card.png", winnerId, 9,
                40_000L, 50_000L, 1_000L, 2, now, AuctionStatus.ENDED, 3L,
                now, now
        );
    }
}
