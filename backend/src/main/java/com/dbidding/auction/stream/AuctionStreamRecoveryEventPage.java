package com.dbidding.auction.stream;

import java.util.List;

public record AuctionStreamRecoveryEventPage(
        List<AuctionStreamRecoveryEventResponse> content,
        int page,
        int totalPages,
        long totalElements
) {
}
