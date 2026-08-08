package com.dbidding.notification.recovery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.notification.Notification;
import com.dbidding.notification.NotificationRepository;
import com.dbidding.notification.NotificationService;
import com.dbidding.notification.NotificationType;
import com.dbidding.wishlist.WishlistService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationReconciliationServiceTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 8, 4, 12, 0);

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private WishlistService wishlistService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationReconciliationService reconciliationService;

    @Test
    void 경매_생성_알림이_없는_찜_유저에게만_복구_알림을_보낸다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.OPEN);
        given(auctionRepository.findByStatusInAndOpenTimeGreaterThanEqual(anyList(), any())).willReturn(List.of(auction));
        given(wishlistService.findUserIdsByCardId(auction.getItemId())).willReturn(List.of(1, 2));
        given(notificationRepository.existsByUserIdAndAuctionIdAndTypeAndBidId(1, 1, NotificationType.AUCTION_OPENED, Notification.NO_BID)).willReturn(true);
        given(notificationRepository.existsByUserIdAndAuctionIdAndTypeAndBidId(2, 1, NotificationType.AUCTION_OPENED, Notification.NO_BID)).willReturn(false);

        reconciliationService.recoverAuctionOpenedNotifications(now.minusMinutes(10));

        verify(notificationService, never()).save(1, 1, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다.");
        verify(notificationService).save(2, 1, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다.");
    }

    @Test
    void 라이브_경로와_레이스가_나서_유니크_제약_위반이_나도_예외를_삼키고_계속한다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.OPEN);
        given(auctionRepository.findByStatusInAndOpenTimeGreaterThanEqual(anyList(), any())).willReturn(List.of(auction));
        given(wishlistService.findUserIdsByCardId(auction.getItemId())).willReturn(List.of(1));
        given(notificationRepository.existsByUserIdAndAuctionIdAndTypeAndBidId(1, 1, NotificationType.AUCTION_OPENED, Notification.NO_BID))
                .willReturn(false);
        given(notificationService.save(1, 1, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        reconciliationService.recoverAuctionOpenedNotifications(now.minusMinutes(10));

        verify(notificationService).save(1, 1, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다.");
    }

    @Test
    void 낙찰된_경매는_낙찰자와_판매자_모두에게_복구_알림을_보낸다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.ENDED);
        Bid winningBid = bid(10L, 5, auction, 50_000L, BidStatus.WON);
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of(auction));
        given(bidRepository.findByAuctionIdAndStatus(1, BidStatus.WON)).willReturn(Optional.of(winningBid));
        given(notificationRepository.existsByUserIdAndAuctionIdAndTypeAndBidId(any(), any(), any(), any())).willReturn(false);

        reconciliationService.recoverAuctionClosedNotifications(now.minusMinutes(20));

        verify(notificationService).save(5, 1, NotificationType.AUCTION_WON, "리자몽 EX 카드 경매에 낙찰되었습니다.");
        verify(notificationService).save(2, 1, NotificationType.AUCTION_WON, "리자몽 EX 카드 경매가 낙찰되었습니다.");
    }

    @Test
    void 유찰된_경매는_판매자에게만_복구_알림을_보낸다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.FAILED);
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of(auction));
        given(bidRepository.findByAuctionIdAndStatus(1, BidStatus.WON)).willReturn(Optional.empty());
        given(notificationRepository.existsByUserIdAndAuctionIdAndTypeAndBidId(2, 1, NotificationType.AUCTION_UNSOLD, Notification.NO_BID)).willReturn(false);

        reconciliationService.recoverAuctionClosedNotifications(now.minusMinutes(20));

        verify(notificationService).save(2, 1, NotificationType.AUCTION_UNSOLD, "리자몽 EX 카드 경매가 유찰되었습니다.");
    }

    @Test
    void 활성_경매도_최근_종료된_경매도_없으면_상회입찰_복구를_바로_종료한다() {
        given(bidRepository.findAuctionIdsByStatus(BidStatus.LEADING)).willReturn(List.of());
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of());

        reconciliationService.recoverOutbidNotifications(now.minusMinutes(10));

        verify(bidRepository, never()).findLatestBidPerBidderByAuctionIdIn(anyCollection());
    }

    @Test
    void 최신_bid가_leading이면_상회입찰_알림을_보내지_않는다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.OPEN);
        Bid leadingBid = bid(1L, 3, auction, 60_000L, BidStatus.LEADING);
        given(bidRepository.findAuctionIdsByStatus(BidStatus.LEADING)).willReturn(List.of(1));
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of());
        given(bidRepository.findLatestBidPerBidderByAuctionIdIn(Set.of(1))).willReturn(List.of(leadingBid));

        reconciliationService.recoverOutbidNotifications(now.minusMinutes(10));

        verify(notificationService, never()).save(any(), any(), any(), any());
    }

    @Test
    void 최신_bid가_outbid고_이후_알림이_없으면_상회입찰_복구_알림을_보낸다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.OPEN);
        Bid outbidBid = bid(2L, 3, auction, 55_000L, BidStatus.OUTBID);
        given(bidRepository.findAuctionIdsByStatus(BidStatus.LEADING)).willReturn(List.of(1));
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of());
        given(bidRepository.findLatestBidPerBidderByAuctionIdIn(Set.of(1))).willReturn(List.of(outbidBid));
        given(notificationRepository.existsByUserIdAndAuctionIdAndTypeAndBidId(
                3, 1, NotificationType.OUTBID, outbidBid.getId()
        )).willReturn(false);

        reconciliationService.recoverOutbidNotifications(now.minusMinutes(10));

        verify(notificationService).saveForBid(3, 1, NotificationType.OUTBID, outbidBid.getId(), "55,000원에 상회 입찰이 발생했습니다.");
    }

    @Test
    void 최신_bid가_outbid여도_이미_알림이_갔으면_다시_보내지_않는다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.OPEN);
        Bid outbidBid = bid(2L, 3, auction, 55_000L, BidStatus.OUTBID);
        given(bidRepository.findAuctionIdsByStatus(BidStatus.LEADING)).willReturn(List.of(1));
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of());
        given(bidRepository.findLatestBidPerBidderByAuctionIdIn(Set.of(1))).willReturn(List.of(outbidBid));
        given(notificationRepository.existsByUserIdAndAuctionIdAndTypeAndBidId(
                3, 1, NotificationType.OUTBID, outbidBid.getId()
        )).willReturn(true);

        reconciliationService.recoverOutbidNotifications(now.minusMinutes(10));

        verify(notificationService, never()).saveForBid(any(), any(), any(), any(), any());
    }

    @Test
    void 상회입찰_직후_경매가_종료돼_LEADING이_사라져도_outbid_유저를_복구한다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.ENDED);
        Bid outbidBid = bid(2L, 3, auction, 55_000L, BidStatus.OUTBID);
        given(bidRepository.findAuctionIdsByStatus(BidStatus.LEADING)).willReturn(List.of());
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of(auction));
        given(bidRepository.findLatestBidPerBidderByAuctionIdIn(Set.of(1))).willReturn(List.of(outbidBid));
        given(notificationRepository.existsByUserIdAndAuctionIdAndTypeAndBidId(
                3, 1, NotificationType.OUTBID, outbidBid.getId()
        )).willReturn(false);

        reconciliationService.recoverOutbidNotifications(now.minusMinutes(10));

        verify(notificationService).saveForBid(3, 1, NotificationType.OUTBID, outbidBid.getId(), "55,000원에 상회 입찰이 발생했습니다.");
    }

    private Auction auction(Integer id, String auctionName, AuctionStatus status) {
        Auction auction = Auction.builder()
                .sellerId(2)
                .itemId(10)
                .auctionName(auctionName)
                .description("카드 상태 설명")
                .startPrice(42_000L)
                .buyNowPrice(100_000L)
                .deliveryFee(3_000L)
                .openTime(now.minusHours(2))
                .estimatedCloseTime(now.plusHours(1))
                .closeTime(now.plusHours(1))
                .bidPriceUnit(1_000L)
                .hyped(false)
                .build();
        ReflectionTestUtils.setField(auction, "id", id);
        ReflectionTestUtils.setField(auction, "status", status);
        return auction;
    }

    private Bid bid(Long id, Integer bidderId, Auction auction, Long bidPrice, BidStatus status) {
        Bid bid = new Bid(bidderId, auction, bidPrice, now.minusMinutes(5), status);
        ReflectionTestUtils.setField(bid, "id", id);
        return bid;
    }
}
