package com.dbidding.wallet.dto;

import jakarta.validation.constraints.Positive;

public record WalletTransactionRequest(@Positive long amount) {
}
