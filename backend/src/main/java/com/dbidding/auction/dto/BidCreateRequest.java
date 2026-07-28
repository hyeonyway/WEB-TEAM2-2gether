package com.dbidding.auction.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BidCreateRequest(
        @NotNull @Min(1) Long price
) {
}
