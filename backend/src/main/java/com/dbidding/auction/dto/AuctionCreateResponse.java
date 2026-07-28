package com.dbidding.auction.dto;

import com.dbidding.auction.domain.AuctionStatus;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record AuctionCreateResponse(
        Integer id,
        AuctionStatus status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Long version
) {
}
