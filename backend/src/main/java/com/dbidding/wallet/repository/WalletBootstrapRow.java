package com.dbidding.wallet.repository;

/** Redis 지갑 bootstrap용 MySQL projection 집계 row. */
public interface WalletBootstrapRow {
    Integer getUserId();
    Long getPoint();
    Long getProjectionVersion();
    Long getFrozenBalance();
}
