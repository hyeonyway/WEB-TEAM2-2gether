package com.dbidding.auction.dto;

import com.dbidding.wallet.domain.WalletAmountPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BidCreateRequest(
        @NotNull @Min(1) @Max(WalletAmountPolicy.MAX_BALANCE) Long price
) {
}
