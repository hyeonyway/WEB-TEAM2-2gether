package com.dbidding.card.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.card.repository.ItemDailyStatisticRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "statistics.scheduler.enabled=false",
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class StatisticAggregationMySqlIntegrationTest {
    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("dbidding");

    @Autowired
    private DailyStatisticAggregationService aggregationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ItemDailyStatisticRepository dailyStatisticRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from bids");
        jdbcTemplate.update("delete from auctions");
        jdbcTemplate.update("delete from market_daily_statistics");
        jdbcTemplate.update("delete from item_statistics");
        jdbcTemplate.update("delete from item_daily_statistics");
        jdbcTemplate.update("delete from card_metadata");
        jdbcTemplate.update("delete from card_sets");
        jdbcTemplate.update("delete from users");

        jdbcTemplate.update("""
                insert into users (
                    id, email, nickname, role, status, encrypted_password, salt
                ) values (
                    1, 'seller@test.local', 'seller', 'USER', 'ACTIVE',
                    repeat('a', 64), repeat('b', 32)
                ), (
                    2, 'bidder@test.local', 'bidder', 'USER', 'ACTIVE',
                    repeat('c', 64), repeat('d', 32)
                )
                """);
        jdbcTemplate.update("insert into card_sets (id, name, code) values (1, '테스트 세트', 'TEST')");
        jdbcTemplate.update("""
                insert into card_metadata (
                    id, card_set_id, name, language, psa_grade, rarity, image_path
                ) values (1, 1, '테스트 카드', 'KO', '10', 'gold', 'test.webp')
                """);
        jdbcTemplate.update("""
                insert into auctions (
                    id, user_id, item_id, auction_name, description,
                    start_price, current_price, buy_now_price, delivery_fee,
                    status, open_time, estimated_close_time, close_time,
                    bid_count, bid_price_unit, is_hyped, version
                ) values (
                    1, 1, 1, '테스트 경매', '통계 테스트',
                    80000, 120000, 150000, 0,
                    'ENDED', '2026-07-27 09:00:00', '2026-07-27 18:00:00',
                    '2026-07-27 18:00:00', 2, 1000, false, 1
                )
                """);
        jdbcTemplate.update("""
                insert into bids (id, user_id, auction_id, bid_price, created_at, status)
                values (1, 2, 1, 100000, '2026-07-27 12:00:00', 'LOST'),
                       (2, 2, 1, 120000, '2026-07-27 17:00:00', 'WON')
                """);
    }

    @Test
    void 실제_MySQL에서_일별_시장과_30일_누적_통계를_멱등_집계한다() {
        LocalDate date = LocalDate.of(2026, 7, 27);

        aggregationService.aggregate(date);
        aggregationService.aggregate(date);

        assertThat(count("item_daily_statistics")).isEqualTo(1);
        assertThat(count("market_daily_statistics")).isEqualTo(1);
        assertThat(count("item_statistics")).isEqualTo(1);
        assertThat(value("select average_price from market_daily_statistics")).isEqualTo(120_000);
        assertThat(value("select bid_count from market_daily_statistics")).isEqualTo(2);
        assertThat(value("select winning_price_total_30d from market_daily_statistics"))
                .isEqualTo(120_000);
        assertThat(value("select bid_count_30d from item_statistics")).isEqualTo(2);
    }

    @Test
    void 거래가_없는_날도_0건_시장_통계를_생성한다() {
        aggregationService.aggregate(LocalDate.of(2026, 7, 28));

        assertThat(count("market_daily_statistics")).isEqualTo(1);
        assertThat(value("select bid_count from market_daily_statistics")).isZero();
        assertThat(value("select ended_auction_count from market_daily_statistics")).isZero();
    }

    @Test
    void 최근_30일의_최근_두_유효_거래를_native_query로_조회한다() {
        jdbcTemplate.update("""
                insert into item_daily_statistics (
                    item_id, statistics_date, latest_price, average_price,
                    lowest_price, highest_price, bid_count, ended_auction_count
                ) values
                    (1, '2026-07-25', 100000, 100000, 100000, 100000, 3, 1),
                    (1, '2026-07-27', 120000, 120000, 120000, 120000, 5, 1)
                """);

        var candidates = dailyStatisticRepository.findPriceMovementCandidates(
                LocalDate.of(2026, 6, 28), LocalDate.of(2026, 7, 28));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().getCurrentPrice()).isEqualTo(120_000L);
        assertThat(candidates.getFirst().getPreviousPrice()).isEqualTo(100_000L);
        assertThat(candidates.getFirst().getBidCount()).isEqualTo(5);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private long value(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }
}
