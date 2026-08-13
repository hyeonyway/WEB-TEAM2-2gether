package com.dbidding.wallet.repository;

/** Redis 지갑 state miss 초기화용 HELD hold row. */
public interface WalletHeldHoldRow {
    Integer getUserId();
    Integer getAuctionId();
    long getAmount();
}
