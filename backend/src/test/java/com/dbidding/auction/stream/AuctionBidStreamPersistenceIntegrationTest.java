package com.dbidding.auction.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.service.WalletService;
import java.time.Instant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "statistic.scheduler.enabled=false",
        "auction.closing.scheduler.enabled=false",
        "auction.deadline.scheduler.enabled=false",
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Transactional
class AuctionBidStreamPersistenceIntegrationTest {
    private static final int AUCTION_ID = 90_001;
    private static final int FIRST_BIDDER_ID = 1_001;
    private static final int SECOND_BIDDER_ID = 1_002;
    private static final int SELLER_ID = 1_003;

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("dbidding");

    @Autowired
    private AuctionBidStreamPersistenceService persistenceService;
    @Autowired
    private WalletService walletService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, nickname, role, status, encrypted_password, salt)
                VALUES
                    (?, 'first@test.local', 'first', 'USER', 'ACTIVE', REPEAT('a', 64), REPEAT('b', 32)),
                    (?, 'second@test.local', 'second', 'USER', 'ACTIVE', REPEAT('c', 64), REPEAT('d', 32)),
                    (?, 'seller@test.local', 'seller', 'USER', 'ACTIVE', REPEAT('e', 64), REPEAT('f', 32))
                """, FIRST_BIDDER_ID, SECOND_BIDDER_ID, SELLER_ID);
        jdbcTemplate.update("INSERT INTO card_sets (id, name, code) VALUES (90001, 'stream', 'STREAM')");
        jdbcTemplate.update("INSERT INTO card_metadata (id, card_set_id, name) VALUES (90001, 90001, 'stream card')");
        jdbcTemplate.update("""
                INSERT INTO auctions (
                    id, user_id, item_id, auction_name, description,
                    start_price, current_price, buy_now_price, delivery_fee,
                    status, open_time, estimated_close_time, close_time,
                    bid_count, bid_price_unit, is_hyped
                ) VALUES (?, ?, 90001, 'stream auction', 'stream auction',
                    10000, 10000, 50000, 0, 'OPEN', UTC_TIMESTAMP(6),
                    DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR),
                    DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR), 1, 1000, FALSE)
                """, AUCTION_ID, SELLER_ID);
        jdbcTemplate.update("""
                INSERT INTO bids (user_id, auction_id, bid_price, status)
                VALUES (?, ?, 10000, 'LEADING')
                """, FIRST_BIDDER_ID, AUCTION_ID);
        jdbcTemplate.update("""
                INSERT INTO wallets (id, user_id, point)
                VALUES (1001, ?, 100000), (1002, ?, 100000)
                """, FIRST_BIDDER_ID, SECOND_BIDDER_ID);
        jdbcTemplate.update("""
                INSERT INTO wallet_holds (wallet_id, auction_id, amount, status)
                VALUES (1001, ?, 10000, 'HELD')
                """, AUCTION_ID);
    }

    @Test
    void 상회입찰과_즉시낙찰_이벤트가_기존_DB_상태전이를_재현한다() {
        persistenceService.persistAll(java.util.List.of(normalBid()));
        entityManager.flush();

        assertThat(bidStatus(FIRST_BIDDER_ID)).isEqualTo("OUTBID");
        assertThat(bidStatus(SECOND_BIDDER_ID)).isEqualTo("LEADING");
        assertThat(auctionStatus()).isEqualTo("OPEN");
        assertThat(auctionLong("current_price")).isEqualTo(11_000L);
        assertThat(auctionLong("bid_count")).isEqualTo(2L);
        assertThat(auctionLong("last_bid_event_version")).isEqualTo(1L);
        assertThat(balance(FIRST_BIDDER_ID)).isEqualTo(new WalletBalanceResponse(100_000L, 0L, 100_000L));
        assertThat(balance(SECOND_BIDDER_ID)).isEqualTo(new WalletBalanceResponse(100_000L, 11_000L, 89_000L));
        assertThat(holdStatus(FIRST_BIDDER_ID)).isEqualTo("RELEASED");
        assertThat(holdStatus(SECOND_BIDDER_ID)).isEqualTo("HELD");

        persistenceService.persistAll(java.util.List.of(buyNow()));
        entityManager.flush();

        assertThat(bidStatus(FIRST_BIDDER_ID)).isEqualTo("WON");
        assertThat(bidStatus(SECOND_BIDDER_ID)).isEqualTo("OUTBID");
        assertThat(auctionStatus()).isEqualTo("ENDED");
        assertThat(auctionLong("current_price")).isEqualTo(50_000L);
        assertThat(auctionLong("bid_count")).isEqualTo(3L);
        assertThat(auctionLong("last_bid_event_version")).isEqualTo(2L);
        assertThat(balance(FIRST_BIDDER_ID)).isEqualTo(new WalletBalanceResponse(50_000L, 0L, 50_000L));
        assertThat(balance(SECOND_BIDDER_ID)).isEqualTo(new WalletBalanceResponse(100_000L, 0L, 100_000L));
        assertThat(holdStatus(FIRST_BIDDER_ID)).isEqualTo("CAPTURED");
        assertThat(holdStatus(SECOND_BIDDER_ID)).isEqualTo("RELEASED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auction_bid_event_inbox WHERE auction_id = ?", Integer.class, AUCTION_ID))
                .isEqualTo(2);
    }

    private BidAcceptedStreamEvent normalBid() {
        return event("1-0", BidStreamEventType.BID_ACCEPTED, 1L, SECOND_BIDDER_ID, FIRST_BIDDER_ID,
                11_000L, 2, AuctionStatus.OPEN);
    }

    private BidAcceptedStreamEvent buyNow() {
        return event("2-0", BidStreamEventType.BUY_NOW, 2L, FIRST_BIDDER_ID, SECOND_BIDDER_ID,
                50_000L, 3, AuctionStatus.ENDED);
    }

    private BidAcceptedStreamEvent event(
            String streamId,
            BidStreamEventType type,
            Long version,
            Integer bidderId,
            Integer previousBidderId,
            Long price,
            Integer bidCount,
            AuctionStatus status
    ) {
        Instant now = Instant.now();
        return new BidAcceptedStreamEvent(
                streamId, type, AUCTION_ID, version, bidderId, price, previousBidderId,
                "stream-" + streamId, "a".repeat(64), price, bidCount,
                status == AuctionStatus.ENDED ? now : now.plus(java.time.Duration.ofHours(2)), status, now
        );
    }

    private String bidStatus(Integer bidderId) {
        return jdbcTemplate.queryForObject("""
                SELECT status FROM bids
                WHERE auction_id = ? AND user_id = ?
                ORDER BY id DESC LIMIT 1
                """, String.class, AUCTION_ID, bidderId);
    }

    private String holdStatus(Integer userId) {
        return jdbcTemplate.queryForObject("""
                SELECT wh.status FROM wallet_holds wh
                JOIN wallets w ON w.id = wh.wallet_id
                WHERE wh.auction_id = ? AND w.user_id = ?
                ORDER BY wh.id DESC LIMIT 1
                """, String.class, AUCTION_ID, userId);
    }

    private WalletBalanceResponse balance(Integer userId) {
        return walletService.getBalance(userId);
    }

    private String auctionStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, AUCTION_ID);
    }

    private Long auctionLong(String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM auctions WHERE id = ?", Long.class, AUCTION_ID);
    }
}
