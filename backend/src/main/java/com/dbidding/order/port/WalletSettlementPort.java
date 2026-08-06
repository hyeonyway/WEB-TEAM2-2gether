package com.dbidding.order.port;

public interface WalletSettlementPort {

    void payoutToSeller(Integer sellerId, Integer orderId, long amount);

    void refundToBuyer(Integer buyerId, Integer orderId, long amount);
}
