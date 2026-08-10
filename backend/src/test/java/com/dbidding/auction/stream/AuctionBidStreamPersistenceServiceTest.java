package com.dbidding.auction.stream;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import java.util.List;
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
    void 배치의_중복확인과_경매잠금과_최고입찰조회는_각각_한번만_수행한다() {
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
        given(inboxRepository.findByStreamIdIn(anyCollection())).willReturn(List.of());
        given(auctionRepository.findByIdInForUpdate(anyCollection())).willReturn(List.of(auction));
        given(auction.getId()).willReturn(10);
        given(auction.getLastBidEventVersion()).willReturn(0L);
        given(auction.isNextBidEventVersion(org.mockito.ArgumentMatchers.anyLong())).willReturn(true);
        given(bidRepository.findByAuctionIdInAndStatus(anyCollection(), org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of());
        given(auction.applyStreamBid(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(true);

        service.persistAll(List.of(event("1-0", 1L, 2, null), event("2-0", 2L, 3, 2)));

        verify(inboxRepository, times(1)).findByStreamIdIn(anyCollection());
        verify(auctionRepository, times(1)).findByIdInForUpdate(anyCollection());
        verify(bidRepository, times(1)).findByAuctionIdInAndStatus(anyCollection(), org.mockito.ArgumentMatchers.any());
        verify(inboxRepository, times(1)).saveAll(anyList());
        verify(bidRepository, times(1)).saveAll(anyList());
    }

    private BidAcceptedStreamEvent event(String streamId, Long version, Integer bidderId, Integer previousBidderId) {
        return new BidAcceptedStreamEvent(
                streamId, BidStreamEventType.BID_ACCEPTED, 10, version, bidderId, 10_000L + version, previousBidderId,
                "request-" + version, "a".repeat(64), 10_000L + version, version.intValue(),
                Instant.parse("2027-08-11T12:00:00Z"), com.dbidding.auction.domain.AuctionStatus.OPEN,
                Instant.parse("2026-08-10T12:00:00Z")
        );
    }
}
