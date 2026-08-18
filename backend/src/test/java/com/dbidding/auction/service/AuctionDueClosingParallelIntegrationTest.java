package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.auction.event.AuctionEventPublisher;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "statistic.scheduler.enabled=false",
        "auction.closing.scheduler.enabled=false",
        "auction.deadline.scheduler.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=2",
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class AuctionDueClosingParallelIntegrationTest {
    private static final List<Integer> AUCTION_IDS = List.of(1, 2, 3, 4);

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("dbidding");

    @Autowired
    private AuctionDueClosingService auctionDueClosingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private AuctionEventPublisher auctionEventPublisher;
    @BeforeEach
    void setUp() {
        insertFixtures();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM point_records WHERE auction_id IN (1, 2, 3, 4)");
        jdbcTemplate.update("DELETE FROM wallet_holds WHERE auction_id IN (1, 2, 3, 4)");
        jdbcTemplate.update("DELETE FROM wallets WHERE user_id IN (2, 3, 4, 5)");
        jdbcTemplate.update("DELETE FROM orders WHERE auction_id IN (1, 2, 3, 4)");
        jdbcTemplate.update("DELETE FROM bids WHERE auction_id IN (1, 2, 3, 4)");
        jdbcTemplate.update("DELETE FROM auctions WHERE id IN (1, 2, 3, 4)");
        jdbcTemplate.update("DELETE FROM card_metadata WHERE id IN (1, 2, 3, 4)");
        jdbcTemplate.update("DELETE FROM card_sets WHERE id = 1");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (1, 2, 3, 4, 5)");
    }

    @Test
    @Timeout(10)
    void 제한된_커넥션_풀에서도_병렬_마감은_각_경매를_독립_트랜잭션으로_정산한다() {
        var responses = auctionDueClosingService.closeDueAuctions(Instant.now(), 100);

        assertThat(responses).extracting(response -> response.auctionId()).containsExactlyElementsOf(AUCTION_IDS);
        assertThat(count("SELECT COUNT(*) FROM auctions WHERE id IN (1, 2, 3, 4) AND status = 'ENDED'"))
                .isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM wallet_holds WHERE auction_id IN (1, 2, 3, 4) AND status = 'CAPTURED'"))
                .isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM point_records WHERE auction_id IN (1, 2, 3, 4) "
                + "AND transaction_type = 'AUCTION_CAPTURE'"))
                .isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM wallets WHERE user_id IN (2, 3, 4, 5) AND point = 88000"))
                .isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM orders WHERE auction_id IN (1, 2, 3, 4) "
                + "AND status = 'PENDING_CONFIRM' AND price = 12000"))
                .isEqualTo(4);
    }

    private void insertFixtures() {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, nickname, role, status, encrypted_password, salt)
                VALUES
                    (1, 'seller@test.local', 'seller', 'USER', 'ACTIVE', REPEAT('a', 64), REPEAT('b', 32)),
                    (2, 'winner-2@test.local', 'winner-2', 'USER', 'ACTIVE', REPEAT('c', 64), REPEAT('d', 32)),
                    (3, 'winner-3@test.local', 'winner-3', 'USER', 'ACTIVE', REPEAT('e', 64), REPEAT('f', 32)),
                    (4, 'winner-4@test.local', 'winner-4', 'USER', 'ACTIVE', REPEAT('g', 64), REPEAT('h', 32)),
                    (5, 'winner-5@test.local', 'winner-5', 'USER', 'ACTIVE', REPEAT('i', 64), REPEAT('j', 32))
                """);
        jdbcTemplate.update("INSERT INTO card_sets (id, name, code) VALUES (1, '병렬 마감 세트', 'PARALLEL-CLOSE')");
        jdbcTemplate.update("""
                INSERT INTO card_metadata (id, card_set_id, name)
                VALUES (1, 1, '카드 1'), (2, 1, '카드 2'), (3, 1, '카드 3'), (4, 1, '카드 4')
                """);
        jdbcTemplate.update("""
                INSERT INTO auctions (
                    id, user_id, item_id, auction_name, description, start_price, current_price, buy_now_price,
                    delivery_fee, status, open_time, estimated_close_time, close_time, bid_count, bid_price_unit, is_hyped
                ) VALUES
                    (1, 1, 1, '마감 1', '병렬 마감', 10000, 12000, 100000, 0, 'OPEN',
                     DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 2 HOUR), DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE),
                     DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE), 1, 1000, FALSE),
                    (2, 1, 2, '마감 2', '병렬 마감', 10000, 12000, 100000, 0, 'OPEN',
                     DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 2 HOUR), DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE),
                     DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE), 1, 1000, FALSE),
                    (3, 1, 3, '마감 3', '병렬 마감', 10000, 12000, 100000, 0, 'OPEN',
                     DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 2 HOUR), DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE),
                     DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE), 1, 1000, FALSE),
                    (4, 1, 4, '마감 4', '병렬 마감', 10000, 12000, 100000, 0, 'OPEN',
                     DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 2 HOUR), DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE),
                     DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE), 1, 1000, FALSE)
                """);
        jdbcTemplate.update("""
                INSERT INTO bids (id, user_id, auction_id, bid_price, status)
                VALUES (1, 2, 1, 12000, 'LEADING'), (2, 3, 2, 12000, 'LEADING'),
                       (3, 4, 3, 12000, 'LEADING'), (4, 5, 4, 12000, 'LEADING')
                """);
        jdbcTemplate.update("""
                INSERT INTO wallets (id, user_id, point)
                VALUES (1, 2, 100000), (2, 3, 100000), (3, 4, 100000), (4, 5, 100000)
                """);
        jdbcTemplate.update("""
                INSERT INTO wallet_holds (id, wallet_id, auction_id, amount, status)
                VALUES (1, 1, 1, 12000, 'HELD'), (2, 2, 2, 12000, 'HELD'),
                       (3, 3, 3, 12000, 'HELD'), (4, 4, 4, 12000, 'HELD')
                """);
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
