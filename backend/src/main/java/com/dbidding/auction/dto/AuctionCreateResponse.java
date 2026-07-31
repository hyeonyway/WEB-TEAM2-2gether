package com.dbidding.auction.dto;

import com.dbidding.auction.domain.AuctionStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record AuctionCreateResponse(
        Integer id,
        AuctionStatus status,
        @JsonProperty("starts_at") LocalDateTime startsAt,
        @JsonProperty("ends_at") LocalDateTime endsAt,
        Long version
) {
}
