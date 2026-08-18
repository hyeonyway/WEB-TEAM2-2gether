package com.dbidding.auction.service.dblock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.dbidding.auction.repository.BidRepository;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.service.CardService;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "statistic.scheduler.enabled=false",
        "auction.closing.scheduler.enabled=false",
        "auction.deadline.scheduler.enabled=false",
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class DbAuctionQueryTransactionIntegrationTest {
    private static final int USER_ID = 99001;
    private static final int CARD_SET_ID = 99001;
    private static final int CARD_ID = 99001;
    private static final int AUCTION_ID = 99001;

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("dbidding");

    @Autowired private DbAuctionQueryService service;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSource dataSource;

    @MockitoBean private CardService cardService;
    @MockitoBean private WalletService walletService;
    @MockitoBean private BidRepository bidRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, nickname, role, status, encrypted_password, salt)
                VALUES (?, 'db-query-tx@test.local', 'db-query-tx', 'USER', 'ACTIVE', REPEAT('a', 64), REPEAT('b', 32))
                """, USER_ID);
        jdbcTemplate.update(
                "INSERT INTO card_sets (id, name, code) VALUES (?, 'DB 조회 트랜잭션 세트', 'DB-QUERY-TX')",
                CARD_SET_ID);
        jdbcTemplate.update(
                "INSERT INTO card_metadata (id, card_set_id, name, image_path) VALUES (?, ?, '피카츄', '/cards/pikachu.png')",
                CARD_ID, CARD_SET_ID);
        jdbcTemplate.update("""
                INSERT INTO auctions (
                    id, user_id, item_id, auction_name, description, start_price, current_price, buy_now_price,
                    delivery_fee, status, open_time, estimated_close_time, close_time, bid_count,
                    bid_price_unit, is_hyped
                ) VALUES (?, ?, ?, 'DB 경계 경매', '설명', 10000, 10000, 50000,
                    0, 'OPEN', UTC_TIMESTAMP(6), DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR),
                    DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR), 0, 1000, FALSE)
                """, AUCTION_ID, USER_ID, CARD_ID);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM images WHERE auction_id = ?", AUCTION_ID);
        jdbcTemplate.update("DELETE FROM bids WHERE auction_id = ?", AUCTION_ID);
        jdbcTemplate.update("DELETE FROM auctions WHERE id = ?", AUCTION_ID);
        jdbcTemplate.update("DELETE FROM card_metadata WHERE id = ?", CARD_ID);
        jdbcTemplate.update("DELETE FROM card_sets WHERE id = ?", CARD_SET_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", USER_ID);
    }

    @Test
    void DB_fallback은_read_only_트랜잭션에서_DTO를_완성한다() throws Exception {
        AtomicBoolean transactionActive = new AtomicBoolean();
        AtomicBoolean transactionReadOnly = new AtomicBoolean();
        given(cardService.getCardSnapshot(CARD_ID)).willAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            transactionReadOnly.set(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return new CardSnapshot(
                    CARD_ID, "피카츄", "DB 조회 트랜잭션 세트", "PSA 10", "JP", "/cards/pikachu.png");
        });

        var detail = service.getDetail(null, AUCTION_ID);
        String serializedOutsideTransaction = objectMapper.writeValueAsString(detail);

        assertThat(transactionActive).isTrue();
        assertThat(transactionReadOnly).isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(serializedOutsideTransaction).contains("\"id\":" + AUCTION_ID);
        assertThat(serializedOutsideTransaction).contains("\"description\":\"설명\"");
    }

    @Test
    void DB_입찰_컨텍스트는_지갑과_경매를_같은_트랜잭션에서_조회한다() {
        AtomicReference<Object> walletConnectionResource = new AtomicReference<>();
        AtomicReference<Object> bidConnectionResource = new AtomicReference<>();
        given(walletService.getBalance(USER_ID)).willAnswer(invocation -> {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            walletConnectionResource.set(TransactionSynchronizationManager.getResource(dataSource));
            return new WalletBalanceResponse(100_000L, 20_000L, 80_000L);
        });
        given(bidRepository.findFirstByAuctionIdAndBidderIdOrderByCreatedAtDescIdDesc(
                AUCTION_ID, USER_ID)).willAnswer(invocation -> {
            bidConnectionResource.set(TransactionSynchronizationManager.getResource(dataSource));
            return Optional.empty();
        });
        given(bidRepository.findByAuctionIdOrderByCreatedAtDescIdDesc(
                AUCTION_ID, PageRequest.of(0, 5)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));

        var response = service.getBidContext(USER_ID, AUCTION_ID);

        assertThat(response.wallet().availableBalance()).isEqualTo(80_000L);
        assertThat(walletConnectionResource.get()).isNotNull();
        assertThat(bidConnectionResource.get()).isSameAs(walletConnectionResource.get());
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }
}
