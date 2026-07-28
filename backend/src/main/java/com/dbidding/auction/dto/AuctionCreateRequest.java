package com.dbidding.auction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AuctionCreateRequest(
        @NotNull Integer itemId,
        @NotBlank String auctionName,
        @NotBlank String description,
        @NotEmpty List<String> imageUploadTokens,
        @NotNull @Min(1) Long startPrice,
        @NotNull @Min(1) Long bidIncrement,
        @NotNull @Min(1) Long buyNowPrice,
        @NotNull @Min(1) @Max(24) Integer durationHours,
        @NotNull @Min(0) Long shippingFee
) {
}
