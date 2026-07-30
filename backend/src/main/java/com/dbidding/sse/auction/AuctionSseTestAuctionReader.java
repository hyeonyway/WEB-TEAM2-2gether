package com.dbidding.sse.auction;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Profile("sse-load-test")
@Component
@RequiredArgsConstructor
class AuctionSseTestAuctionReader {

    private static final int TEST_USER_ID = 1;

    private final JdbcClient jdbcClient;

    Optional<Snapshot> findRandomActiveAuction() {
        return jdbcClient.sql("""
                        SELECT a.id,
                               a.item_id,
                               c.name,
                               c.psa_grade,
                               c.language,
                               COALESCE(
                                   (SELECT i.image_path
                                      FROM images i
                                     WHERE i.auction_id = a.id
                                     ORDER BY i.id
                                     LIMIT 1),
                                   c.image_path
                               ) AS thumbnail_url,
                               a.user_id AS seller_id,
                               a.start_price,
                               a.current_price,
                               a.bid_price_unit,
                               a.bid_count,
                               a.estimated_close_time,
                               a.status,
                               a.version,
                               (SELECT b.user_id
                                  FROM bids b
                                 WHERE b.auction_id = a.id
                                 ORDER BY b.bid_price DESC, b.id DESC
                                 LIMIT 1) AS current_bidder_id
                          FROM auctions a
                          JOIN card_metadata c ON c.id = a.item_id
                         WHERE a.status IN ('OPEN', 'ENDING')
                           AND a.estimated_close_time > NOW(6)
                           AND EXISTS (
                               SELECT 1
                                 FROM bids participating_bid
                                WHERE participating_bid.auction_id = a.id
                                  AND participating_bid.user_id = :testUserId
                           )
                         ORDER BY RAND()
                         LIMIT 1
                        """)
                .param("testUserId", TEST_USER_ID)
                .query((resultSet, rowNum) -> new Snapshot(
                        resultSet.getInt("id"),
                        resultSet.getInt("item_id"),
                        resultSet.getString("name"),
                        resultSet.getString("psa_grade"),
                        resultSet.getString("language"),
                        resultSet.getString("thumbnail_url"),
                        resultSet.getInt("seller_id"),
                        resultSet.getLong("start_price"),
                        resultSet.getLong("current_price"),
                        resultSet.getLong("bid_price_unit"),
                        resultSet.getInt("bid_count"),
                        resultSet.getObject("estimated_close_time", LocalDateTime.class),
                        resultSet.getString("status"),
                        resultSet.getLong("version"),
                        resultSet.getObject("current_bidder_id", Integer.class)
                ))
                .optional();
    }

    record Snapshot(
            Integer auctionId,
            Integer cardId,
            String cardName,
            String cardPsaGrade,
            String cardLanguage,
            String cardThumbnailUrl,
            Integer sellerId,
            Long startPrice,
            Long currentPrice,
            Long bidIncrement,
            Integer bidCount,
            LocalDateTime endsAt,
            String status,
            Long auctionVersion,
            Integer currentBidderId
    ) {
    }
}
