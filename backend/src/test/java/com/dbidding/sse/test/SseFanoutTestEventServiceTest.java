package com.dbidding.sse.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.dbidding.auction.sse.AuctionSseTestBidApplicationService;
import com.dbidding.auction.sse.AuctionStreamEventType;
import com.dbidding.auction.sse.AuctionStreamPayload;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.notification.NotificationType;
import com.dbidding.notification.dto.NotificationResponse;
import com.dbidding.notification.sse.NotificationSseTestPushService;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.sse.WalletBalanceChangedEvent;
import com.dbidding.wallet.sse.WalletSseTestPushService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SseFanoutTestEventServiceTest {

    @Mock
    private AuctionSseTestBidApplicationService auctionPublisher;
    @Mock
    private NotificationSseTestPushService notificationPublisher;
    @Mock
    private WalletSseTestPushService walletPublisher;

    @Test
    void 조합_발행은_경매_한번과_알림_지갑을_각각_두_유저에게_발행한다() {
        var service = new SseFanoutTestEventService(auctionPublisher, notificationPublisher, walletPublisher);
        var auctionPayload = new AuctionStreamPayload(
                AuctionStreamEventType.BID_PLACED, 10, null, null, null, null, null, null,
                901, 902, null, 40_000L, 41_000L, null, 1_000L, 3,
                Instant.now(), AuctionStatus.OPEN, null, Instant.now(), Instant.now());
        var outbidNotification = new NotificationResponse(1L, 10, NotificationType.OUTBID, 0L, "메시지", false, Instant.now());
        var newBidderNotification = new NotificationResponse(2L, 10, NotificationType.OUTBID, 0L, "메시지", false, Instant.now());
        var outbidWallet = new WalletBalanceChangedEvent(902, new WalletBalanceResponse(1_000_000L, 0L, 1_000_000L, 1L), 1L, Instant.now());
        var newBidderWallet = new WalletBalanceChangedEvent(901, new WalletBalanceResponse(1_001_000L, 0L, 1_001_000L, 2L), 2L, Instant.now());
        given(auctionPublisher.publishBidFor(10)).willReturn(auctionPayload);
        given(notificationPublisher.publishTestPush(902, 10)).willReturn(outbidNotification);
        given(notificationPublisher.publishTestPush(901, 10)).willReturn(newBidderNotification);
        given(walletPublisher.publishTestBalanceChange(902)).willReturn(outbidWallet);
        given(walletPublisher.publishTestBalanceChange(901)).willReturn(newBidderWallet);

        var result = service.publishRandomBidEvent(10, 902, 901);

        assertThat(result.auction()).isEqualTo(auctionPayload);
        assertThat(result.outbidNotification()).isEqualTo(outbidNotification);
        assertThat(result.newBidderNotification()).isEqualTo(newBidderNotification);
        assertThat(result.outbidWallet()).isEqualTo(outbidWallet);
        assertThat(result.newBidderWallet()).isEqualTo(newBidderWallet);
        verify(auctionPublisher).publishBidFor(10);
        verify(notificationPublisher).publishTestPush(902, 10);
        verify(notificationPublisher).publishTestPush(901, 10);
        verify(walletPublisher).publishTestBalanceChange(902);
        verify(walletPublisher).publishTestBalanceChange(901);
    }
}
