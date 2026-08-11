package com.dbidding.auction.stream;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.repository.AuctionBidEventInboxRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.card.service.CardService;
import com.dbidding.order.OrderService;
import com.dbidding.wallet.service.WalletService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuctionBidStreamPersistenceServiceTest {
    @Mock
    private AuctionBidEventInboxRepository inboxRepository;
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private OrderService orderService;
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
                bidRepository,
                walletService,
                orderService,
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

        verify(inboxRepository, times(1)).findByStreamId("1-0");
        verify(auctionRepository, times(1)).findByIdForUpdate(10);
        verify(bidRepository, times(1)).findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(10, com.dbidding.auction.domain.BidStatus.LEADING);
        verify(inboxRepository, times(1)).save(org.mockito.ArgumentMatchers.any());
        verify(bidRepository, times(1)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 버전이_건너뛰면_재시도_대신_경매_pause_대상이_되는_예외를_발생시킨다() {
        AuctionBidStreamPersistenceService service = new AuctionBidStreamPersistenceService(
                inboxRepository, auctionRepository, bidRepository, walletService, orderService, cardService,
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
    void 지갑_충전은_같은_타임라인_inbox에_기록하고_wallet에_반영한다() {
        AuctionBidStreamPersistenceService service = new AuctionBidStreamPersistenceService(
                inboxRepository, auctionRepository, bidRepository, walletService, orderService, cardService,
                auctionEventPublisher, Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC)
        );
        WalletChargedStreamEvent event = new WalletChargedStreamEvent(
                "charge-1", 1, 50_000L, "charge-key", Instant.parse("2026-08-10T12:00:00Z")
        );
        given(inboxRepository.findByStreamId("charge-1")).willReturn(java.util.Optional.empty());

        service.persist(event);

        verify(walletService).charge(1, 50_000L, "charge-key");
        verify(inboxRepository).save(org.mockito.ArgumentMatchers.any());
        verify(auctionRepository, org.mockito.Mockito.never()).findByIdForUpdate(org.mockito.ArgumentMatchers.any());
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
