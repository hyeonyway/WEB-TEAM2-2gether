package com.dbidding.auction.dto;

import com.dbidding.auction.domain.AuctionStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.Builder;

@Builder
public record AuctionCreateResponse(
        Integer id,
        AuctionStatus status,
        @JsonProperty("starts_at") Instant startsAt,
        @JsonProperty("ends_at") Instant endsAt,
        Long version
) {
}
