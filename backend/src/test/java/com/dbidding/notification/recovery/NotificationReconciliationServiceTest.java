package com.dbidding.notification.recovery;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.notification.dto.NotificationInsertRow;
import com.dbidding.notification.service.NotificationService;
import com.dbidding.notification.domain.NotificationType;
import com.dbidding.wishlist.service.WishlistService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationReconciliationServiceTest {

    private final Instant now = Instant.parse("2026-08-04T12:00:00Z");

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private WishlistService wishlistService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationReconciliationService reconciliationService;

    @Test
    void 경매_생성_알림_복구는_찜_유저_전원을_한번에_저장한다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.OPEN);
        given(auctionRepository.findByStatusInAndOpenTimeGreaterThanEqual(anyList(), any())).willReturn(List.of(auction));
        given(wishlistService.groupUserIdsByCardIdIn(List.of(auction.getItemId())))
                .willReturn(Map.of(auction.getItemId(), List.of(1, 2)));

        reconciliationService.recoverAuctionOpenedNotifications(now.minus(Duration.ofMinutes(10)));

        verify(notificationService).insertAllIgnoringDuplicates(
                List.of(
                        NotificationInsertRow.of(1, 1, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."),
                        NotificationInsertRow.of(2, 1, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다.")
                )
        );
    }

    @Test
    void 찜한_유저가_없는_경매는_빈_목록으로_저장을_호출한다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.OPEN);
        given(auctionRepository.findByStatusInAndOpenTimeGreaterThanEqual(anyList(), any())).willReturn(List.of(auction));
        given(wishlistService.groupUserIdsByCardIdIn(List.of(auction.getItemId()))).willReturn(Map.of());

        reconciliationService.recoverAuctionOpenedNotifications(now.minus(Duration.ofMinutes(10)));

        verify(notificationService).insertAllIgnoringDuplicates(List.of());
    }

    @Test
    void 이번_윈도우에_경매가_여러개_열렸으면_행을_한번에_합쳐서_저장한다() {
        Auction firstAuction = auction(1, "리자몽 EX", AuctionStatus.OPEN);
        Auction secondAuction = auction(2, "피카츄", AuctionStatus.OPEN);
        given(auctionRepository.findByStatusInAndOpenTimeGreaterThanEqual(anyList(), any()))
                .willReturn(List.of(firstAuction, secondAuction));
        given(wishlistService.groupUserIdsByCardIdIn(List.of(10, 10)))
                .willReturn(Map.of(10, List.of(1, 2)));

        reconciliationService.recoverAuctionOpenedNotifications(now.minus(Duration.ofMinutes(10)));

        verify(notificationService).insertAllIgnoringDuplicates(
                List.of(
                        NotificationInsertRow.of(1, 1, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."),
                        NotificationInsertRow.of(2, 1, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."),
                        NotificationInsertRow.of(1, 2, NotificationType.AUCTION_OPENED, "피카츄 카드의 경매가 등록되었습니다."),
                        NotificationInsertRow.of(2, 2, NotificationType.AUCTION_OPENED, "피카츄 카드의 경매가 등록되었습니다.")
                )
        );
    }

    @Test
    void 낙찰된_경매는_낙찰자와_판매자_모두에게_복구_알림을_보낸다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.ENDED);
        Bid winningBid = bid(10L, 5, auction, 50_000L, BidStatus.WON);
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of(auction));
        given(bidRepository.findByAuctionIdInAndStatus(List.of(1), BidStatus.WON)).willReturn(List.of(winningBid));

        reconciliationService.recoverAuctionClosedNotifications(now.minus(Duration.ofMinutes(20)));

        verify(notificationService).insertAllIgnoringDuplicates(List.of(
                NotificationInsertRow.of(5, 1, NotificationType.AUCTION_WON, "리자몽 EX 카드 경매에 낙찰되었습니다."),
                NotificationInsertRow.of(2, 1, NotificationType.AUCTION_WON, "리자몽 EX 카드 경매가 낙찰되었습니다.")
        ));
    }

    @Test
    void 유찰된_경매는_판매자에게만_복구_알림을_보낸다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.FAILED);
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of(auction));
        given(bidRepository.findByAuctionIdInAndStatus(List.of(1), BidStatus.WON)).willReturn(List.of());

        reconciliationService.recoverAuctionClosedNotifications(now.minus(Duration.ofMinutes(20)));

        verify(notificationService).insertAllIgnoringDuplicates(List.of(
                NotificationInsertRow.of(2, 1, NotificationType.AUCTION_UNSOLD, "리자몽 EX 카드 경매가 유찰되었습니다.")
        ));
    }

    @Test
    void ENDING_경매는_bids_조인_없이_auctionRepository로_직접_조회한다() {
        given(auctionRepository.findIdsByStatus(AuctionStatus.ENDING)).willReturn(List.of());
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of());

        reconciliationService.recoverEndingOutbidNotifications(now.minus(Duration.ofMinutes(10)));

        verify(auctionRepository).findIdsByStatus(AuctionStatus.ENDING);
        verify(bidRepository, never()).findAuctionIdsByStatus(any());
    }

    @Test
    void ENDING_경매도_최근_종료된_경매도_없으면_상회입찰_복구를_바로_종료한다() {
        given(auctionRepository.findIdsByStatus(AuctionStatus.ENDING)).willReturn(List.of());
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of());

        reconciliationService.recoverEndingOutbidNotifications(now.minus(Duration.ofMinutes(10)));

        verify(bidRepository, never()).findLatestBidPerBidderByAuctionIdIn(anyCollection());
    }

    @Test
    void ENDING_경매에_outbid_bid가_있으면_복구_알림을_보낸다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.ENDING);
        Bid outbidBid = bid(2L, 3, auction, 55_000L, BidStatus.OUTBID);
        given(auctionRepository.findIdsByStatus(AuctionStatus.ENDING)).willReturn(List.of(1));
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of());
        given(bidRepository.findLatestBidPerBidderByAuctionIdIn(Set.of(1))).willReturn(List.of(outbidBid));

        reconciliationService.recoverEndingOutbidNotifications(now.minus(Duration.ofMinutes(10)));

        verify(notificationService).insertAllIgnoringDuplicates(List.of(
                new NotificationInsertRow(3, 1, NotificationType.OUTBID, outbidBid.getId(), "55,000원에 상회 입찰이 발생했습니다.")
        ));
    }

    @Test
    void ENDING_상회입찰_직후_경매가_종료돼도_outbid_유저를_복구한다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.ENDED);
        Bid outbidBid = bid(2L, 3, auction, 55_000L, BidStatus.OUTBID);
        given(auctionRepository.findIdsByStatus(AuctionStatus.ENDING)).willReturn(List.of());
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of(auction));
        given(bidRepository.findLatestBidPerBidderByAuctionIdIn(Set.of(1))).willReturn(List.of(outbidBid));

        reconciliationService.recoverEndingOutbidNotifications(now.minus(Duration.ofMinutes(10)));

        verify(notificationService).insertAllIgnoringDuplicates(List.of(
                new NotificationInsertRow(3, 1, NotificationType.OUTBID, outbidBid.getId(), "55,000원에 상회 입찰이 발생했습니다.")
        ));
    }

    @Test
    void OPEN_경매는_LEADING_bid로_경매_상태_join_없이_후보를_찾는다() {
        given(bidRepository.findAuctionIdsByStatus(BidStatus.LEADING)).willReturn(List.of());
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of());

        reconciliationService.recoverOpenOutbidNotifications(now.minus(Duration.ofMinutes(10)));

        verify(bidRepository).findAuctionIdsByStatus(BidStatus.LEADING);
        verify(auctionRepository, never()).findIdsByStatus(any());
    }

    @Test
    void OPEN_경매도_최근_종료된_경매도_없으면_상회입찰_복구를_바로_종료한다() {
        given(bidRepository.findAuctionIdsByStatus(BidStatus.LEADING)).willReturn(List.of());
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of());

        reconciliationService.recoverOpenOutbidNotifications(now.minus(Duration.ofMinutes(10)));

        verify(bidRepository, never()).findLatestBidPerBidderByAuctionIdIn(anyCollection());
    }

    @Test
    void 최신_bid가_leading이면_상회입찰_알림을_보내지_않는다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.OPEN);
        Bid leadingBid = bid(1L, 3, auction, 60_000L, BidStatus.LEADING);
        given(bidRepository.findAuctionIdsByStatus(BidStatus.LEADING)).willReturn(List.of(1));
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of());
        given(bidRepository.findLatestBidPerBidderByAuctionIdIn(Set.of(1))).willReturn(List.of(leadingBid));

        reconciliationService.recoverOpenOutbidNotifications(now.minus(Duration.ofMinutes(10)));

        verify(notificationService, never()).insertAllIgnoringDuplicates(any());
    }

    @Test
    void 최신_bid가_outbid면_상회입찰_복구_알림을_보낸다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.OPEN);
        Bid outbidBid = bid(2L, 3, auction, 55_000L, BidStatus.OUTBID);
        given(bidRepository.findAuctionIdsByStatus(BidStatus.LEADING)).willReturn(List.of(1));
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of());
        given(bidRepository.findLatestBidPerBidderByAuctionIdIn(Set.of(1))).willReturn(List.of(outbidBid));

        reconciliationService.recoverOpenOutbidNotifications(now.minus(Duration.ofMinutes(10)));

        verify(notificationService).insertAllIgnoringDuplicates(List.of(
                new NotificationInsertRow(3, 1, NotificationType.OUTBID, outbidBid.getId(), "55,000원에 상회 입찰이 발생했습니다.")
        ));
    }

    @Test
    void ENDING_경매도_LEADING_bid를_통해_non_urgent에서_함께_복구된다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.ENDING);
        Bid outbidBid = bid(2L, 3, auction, 55_000L, BidStatus.OUTBID);
        given(bidRepository.findAuctionIdsByStatus(BidStatus.LEADING)).willReturn(List.of(1));
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of());
        given(bidRepository.findLatestBidPerBidderByAuctionIdIn(Set.of(1))).willReturn(List.of(outbidBid));

        reconciliationService.recoverOpenOutbidNotifications(now.minus(Duration.ofMinutes(10)));

        verify(notificationService).insertAllIgnoringDuplicates(List.of(
                new NotificationInsertRow(3, 1, NotificationType.OUTBID, outbidBid.getId(), "55,000원에 상회 입찰이 발생했습니다.")
        ));
    }

    @Test
    void 상회입찰_직후_경매가_종료돼_LEADING이_사라져도_outbid_유저를_복구한다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.ENDED);
        Bid outbidBid = bid(2L, 3, auction, 55_000L, BidStatus.OUTBID);
        given(bidRepository.findAuctionIdsByStatus(BidStatus.LEADING)).willReturn(List.of());
        given(auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(anyList(), any())).willReturn(List.of(auction));
        given(bidRepository.findLatestBidPerBidderByAuctionIdIn(Set.of(1))).willReturn(List.of(outbidBid));

        reconciliationService.recoverOpenOutbidNotifications(now.minus(Duration.ofMinutes(10)));

        verify(notificationService).insertAllIgnoringDuplicates(List.of(
                new NotificationInsertRow(3, 1, NotificationType.OUTBID, outbidBid.getId(), "55,000원에 상회 입찰이 발생했습니다.")
        ));
    }

    @Test
    void 저장중_데드락이_나면_재시도해서_성공한다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.OPEN);
        given(auctionRepository.findByStatusInAndOpenTimeGreaterThanEqual(anyList(), any())).willReturn(List.of(auction));
        given(wishlistService.groupUserIdsByCardIdIn(List.of(auction.getItemId())))
                .willReturn(Map.of(auction.getItemId(), List.of(1)));
        willThrow(new CannotAcquireLockException("deadlock"))
                .willThrow(new CannotAcquireLockException("deadlock"))
                .willDoNothing()
                .given(notificationService).insertAllIgnoringDuplicates(any());

        reconciliationService.recoverAuctionOpenedNotifications(now.minus(Duration.ofMinutes(10)));

        verify(notificationService, times(3)).insertAllIgnoringDuplicates(any());
    }

    @Test
    void 데드락_재시도를_모두_소진하면_예외를_전파한다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.OPEN);
        given(auctionRepository.findByStatusInAndOpenTimeGreaterThanEqual(anyList(), any())).willReturn(List.of(auction));
        given(wishlistService.groupUserIdsByCardIdIn(List.of(auction.getItemId())))
                .willReturn(Map.of(auction.getItemId(), List.of(1)));
        willThrow(new CannotAcquireLockException("deadlock"))
                .given(notificationService).insertAllIgnoringDuplicates(any());

        assertThatThrownBy(() -> reconciliationService.recoverAuctionOpenedNotifications(now.minus(Duration.ofMinutes(10))))
                .isInstanceOf(CannotAcquireLockException.class);
        verify(notificationService, times(3)).insertAllIgnoringDuplicates(any());
    }

    @Test
    void 데드락이_아닌_예외는_재시도_없이_즉시_전파된다() {
        Auction auction = auction(1, "리자몽 EX", AuctionStatus.OPEN);
        given(auctionRepository.findByStatusInAndOpenTimeGreaterThanEqual(anyList(), any())).willReturn(List.of(auction));
        given(wishlistService.groupUserIdsByCardIdIn(List.of(auction.getItemId())))
                .willReturn(Map.of(auction.getItemId(), List.of(1)));
        willThrow(new IllegalStateException("다른 오류"))
                .given(notificationService).insertAllIgnoringDuplicates(any());

        assertThatThrownBy(() -> reconciliationService.recoverAuctionOpenedNotifications(now.minus(Duration.ofMinutes(10))))
                .isInstanceOf(IllegalStateException.class);
        verify(notificationService, times(1)).insertAllIgnoringDuplicates(any());
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
                .openTime(now.minus(Duration.ofHours(2)))
                .estimatedCloseTime(now.plus(Duration.ofHours(1)))
                .closeTime(now.plus(Duration.ofHours(1)))
                .bidPriceUnit(1_000L)
                .hyped(false)
                .build();
        ReflectionTestUtils.setField(auction, "id", id);
        ReflectionTestUtils.setField(auction, "status", status);
        return auction;
    }

    private Bid bid(Long id, Integer bidderId, Auction auction, Long bidPrice, BidStatus status) {
        Bid bid = new Bid(bidderId, auction, bidPrice, now.minus(Duration.ofMinutes(5)), status);
        ReflectionTestUtils.setField(bid, "id", id);
        return bid;
    }

}
