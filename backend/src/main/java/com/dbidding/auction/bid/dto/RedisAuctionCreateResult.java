package com.dbidding.auction.bid.dto;

import com.dbidding.auction.domain.AuctionStatus;
import java.time.Instant;

public record RedisAuctionCreateResult(Integer auctionId, String streamId, AuctionStatus status, Instant occurredAt,
                                       Instant closeTime, boolean replayed) {
}
