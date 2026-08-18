package com.dbidding.sse.test;

import com.dbidding.auction.sse.AuctionStreamPayload;
import com.dbidding.notification.dto.NotificationResponse;
import com.dbidding.wallet.sse.WalletBalanceChangedEvent;

/** 조합 발행 1건이 만든 auction/notification/wallet 3채널 payload를 한데 묶은 응답. */
public record SseFanoutTestEventResult(
        AuctionStreamPayload auction,
        NotificationResponse outbidNotification,
        NotificationResponse newBidderNotification,
        WalletBalanceChangedEvent outbidWallet,
        WalletBalanceChangedEvent newBidderWallet
) {
}
