package com.dbidding.auction.sse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
                               a.estimated_close_time, a.status,
                               (SELECT b.user_id FROM bids b WHERE b.auction_id = a.id
                                ORDER BY b.bid_price DESC, b.id DESC LIMIT 1) AS current_bidder_id
                          FROM auctions a
                         WHERE a.status IN ('OPEN', 'ENDING') AND a.estimated_close_time > NOW(6)
                           AND EXISTS (SELECT 1 FROM bids b WHERE b.auction_id = a.id AND b.user_id = 1)
                         ORDER BY RAND() LIMIT 1
                        """)
                .query((resultSet, rowNum) -> toSnapshot(resultSet))
                .optional();
    }

    /** 부하테스트 시나리오가 지정한 auctionId 하나를 그대로 조회한다(랜덤 선택 없음). */
    Optional<Snapshot> findAuction(Integer auctionId) {
        return jdbcClient.sql("""
                        SELECT a.id, a.start_price, a.current_price, a.bid_price_unit, a.bid_count,
                               a.estimated_close_time, a.status,
                               (SELECT b.user_id FROM bids b WHERE b.auction_id = a.id
                                ORDER BY b.bid_price DESC, b.id DESC LIMIT 1) AS current_bidder_id
                          FROM auctions a
                         WHERE a.id = :auctionId
                        """)
                .param("auctionId", auctionId)
                .query((resultSet, rowNum) -> toSnapshot(resultSet))
                .optional();
    }

    private Snapshot toSnapshot(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new Snapshot(
                resultSet.getInt("id"), resultSet.getLong("start_price"),
                resultSet.getLong("current_price"), resultSet.getLong("bid_price_unit"),
                resultSet.getInt("bid_count"),
                // MySQL Connector/J가 getObject(column, Instant.class)를 지원하지 않아
                // (SQLException: Conversion not supported for type java.time.Instant)
                // JDBC 경계에서는 LocalDateTime으로 읽고 바로 Instant로 변환한다.
                resultSet.getObject("estimated_close_time", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                resultSet.getString("status"),
                resultSet.getObject("current_bidder_id", Integer.class));
    }

    record Snapshot(Integer auctionId, Long startPrice, Long currentPrice, Long bidIncrement,
                    Integer bidCount, Instant endsAt, String status,
                    Integer currentBidderId) { }
}
