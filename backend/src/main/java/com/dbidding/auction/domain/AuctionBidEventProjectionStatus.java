package com.dbidding.auction.domain;

/** Redis Stream 이벤트의 MySQL projection 처리 상태. */
public enum AuctionBidEventProjectionStatus {
    PENDING,
    PROCESSED,
    ERROR
}
