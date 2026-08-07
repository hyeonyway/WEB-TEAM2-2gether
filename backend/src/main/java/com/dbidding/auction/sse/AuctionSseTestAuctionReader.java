package com.dbidding.auction.sse;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
@RequiredArgsConstructor
class AuctionSseTestAuctionReader {
    private final JdbcClient jdbcClient;

    Optional<Snapshot> findRandomActiveAuction() {
        return jdbcClient.sql("""
                        SELECT a.id, a.start_price, a.current_price, a.bid_price_unit, a.bid_count,
                               a.estimated_close_time, a.status, a.version,
                               (SELECT b.user_id FROM bids b WHERE b.auction_id = a.id
                                ORDER BY b.bid_price DESC, b.id DESC LIMIT 1) AS current_bidder_id
                          FROM auctions a
                         WHERE a.status IN ('OPEN', 'ENDING') AND a.estimated_close_time > NOW(6)
                           AND EXISTS (SELECT 1 FROM bids b WHERE b.auction_id = a.id AND b.user_id = 1)
                         ORDER BY RAND() LIMIT 1
                        """)
                .query((resultSet, rowNum) -> new Snapshot(
                        resultSet.getInt("id"), resultSet.getLong("start_price"),
                        resultSet.getLong("current_price"), resultSet.getLong("bid_price_unit"),
                        resultSet.getInt("bid_count"),
                        resultSet.getObject("estimated_close_time", LocalDateTime.class),
                        resultSet.getString("status"), resultSet.getLong("version"),
                        resultSet.getObject("current_bidder_id", Integer.class)))
                .optional();
    }

    record Snapshot(Integer auctionId, Long startPrice, Long currentPrice, Long bidIncrement,
                    Integer bidCount, LocalDateTime endsAt, String status, Long auctionVersion,
                    Integer currentBidderId) { }
}
