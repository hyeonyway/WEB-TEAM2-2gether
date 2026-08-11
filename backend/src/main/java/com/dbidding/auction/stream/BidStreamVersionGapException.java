package com.dbidding.auction.stream;

/** 누락된 선행 이벤트를 먼저 재생해야만 해소되는 경매 단위의 순서 단절이다. */
public class BidStreamVersionGapException extends InvalidBidStreamEventException {
    public BidStreamVersionGapException(String message) {
        super(message);
    }
}
