package com.dbidding.auction.domain;

import com.dbidding.wallet.domain.WalletAmountPolicy;

/**
 * 경매 개설 시 시작가와 호가 단위의 상한을 나눠서, 둘을 각각 상한까지 채워도
 * 합이 {@link WalletAmountPolicy#MAX_BALANCE}를 넘지 않도록 구조적으로 보장한다 —
 * 최초 입찰 최소가(시작가 + 호가 단위)가 항상 입찰 가능한 범위 안에 들어온다.
 */
public final class AuctionPricePolicy {

    public static final long MAX_START_PRICE = 999_000_000_000L;
    public static final long MAX_BID_INCREMENT = 1_000_000_000L;

    private AuctionPricePolicy() {
    }
}
