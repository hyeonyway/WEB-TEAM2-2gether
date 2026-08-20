package com.dbidding.auction;

/** 입찰자 id를 노출용 별칭(예: "user-12***")으로 마스킹한다. */
public final class BidderAlias {
    private BidderAlias() {
    }

    public static String mask(Integer bidderId) {
        if (bidderId == null) return "";
        String value = String.valueOf(bidderId);
        return value.length() <= 2
                ? "user-" + value + "***"
                : "user-" + value.substring(0, 2) + "***";
    }
}
