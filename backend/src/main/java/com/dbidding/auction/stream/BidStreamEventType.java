package com.dbidding.auction.stream;

public enum BidStreamEventType {
    BID_ACCEPTED("bid.accepted.v1"),
    BUY_NOW("auction.buy-now.v1");

    private final String value;

    BidStreamEventType(String value) {
        this.value = value;
    }

    public static BidStreamEventType from(String value) {
        for (BidStreamEventType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new InvalidBidStreamEventException("지원하지 않는 입찰 Stream 이벤트입니다.");
    }
}
