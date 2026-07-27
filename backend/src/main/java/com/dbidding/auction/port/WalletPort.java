    package com.dbidding.auction.port;

public interface WalletPort {
    WalletSnapshot getWallet(Integer userId);

    WalletSnapshot holdBidAmount(Integer userId, Integer auctionId, long amount);

    WalletSnapshot releaseBidHold(Integer userId, Integer auctionId);

    WalletSnapshot confirmWinningBid(Integer userId, Integer auctionId, long amount);

    record WalletSnapshot(
            long availableBalance,
            long frozenBalance
    ) {
    }
}
