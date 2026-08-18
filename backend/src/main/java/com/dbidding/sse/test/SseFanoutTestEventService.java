package com.dbidding.sse.test;

import com.dbidding.auction.sse.AuctionSseTestBidApplicationService;
import com.dbidding.notification.sse.NotificationSseTestPushService;
import com.dbidding.wallet.sse.WalletSseTestPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 실제 입찰 1건이 만드는 효과(경매 브로드캐스트 + 낙찰 관련자 2명의 notification/wallet 변경)를
 * 재현하는 조합 발행자(#569). 순수 SSE fan-out 부하테스트에서 이벤트 1건당 이 메서드 하나만
 * 호출하면 3채널이 전부 실제 Redis publish 경로로 나간다.
 */
@Service
@Profile("test")
@RequiredArgsConstructor
public class SseFanoutTestEventService {
    private final AuctionSseTestBidApplicationService auctionPublisher;
    private final NotificationSseTestPushService notificationPublisher;
    private final WalletSseTestPushService walletPublisher;

    public SseFanoutTestEventResult publishRandomBidEvent(Integer auctionId, Integer outbidUserId, Integer newBidderUserId) {
        var auctionPayload = auctionPublisher.publishBidFor(auctionId);
        var outbidNotification = notificationPublisher.publishTestPush(outbidUserId, auctionId);
        var newBidderNotification = notificationPublisher.publishTestPush(newBidderUserId, auctionId);
        var outbidWallet = walletPublisher.publishTestBalanceChange(outbidUserId);
        var newBidderWallet = walletPublisher.publishTestBalanceChange(newBidderUserId);
        return new SseFanoutTestEventResult(
                auctionPayload, outbidNotification, newBidderNotification, outbidWallet, newBidderWallet);
    }
}
