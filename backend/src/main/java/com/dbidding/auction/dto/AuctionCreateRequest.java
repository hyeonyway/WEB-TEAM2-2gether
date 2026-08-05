package com.dbidding.auction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AuctionCreateRequest(
        @NotNull Integer itemId,
        @NotBlank String auctionName,
        @NotBlank String description,
        @Size(max = 1000) String sellerMemo,
        @Size(max = 32) String psaCertification,
        @NotEmpty List<String> imageUploadTokens,
        @NotNull @Min(1) Long startPrice,
        @NotNull @Min(1) Long bidIncrement,
        @Positive Long buyNowPrice,
        @NotNull @Min(1) @Max(24) Integer durationHours,
        @NotNull @Min(0) Long shippingFee,
        @Size(max = 16) String gradeType,
        @Size(max = 32) String selfGrade,
        @Size(max = 8) String psaGrade
) {
    public AuctionCreateRequest(
            Integer itemId,
            String auctionName,
            String description,
            String sellerMemo,
            String psaCertification,
            List<String> imageUploadTokens,
            Long startPrice,
            Long bidIncrement,
            Long buyNowPrice,
            Integer durationHours,
            Long shippingFee
    ) {
        this(itemId, auctionName, description, sellerMemo, psaCertification, imageUploadTokens,
                startPrice, bidIncrement, buyNowPrice, durationHours, shippingFee, null, null, null);
    }
}
