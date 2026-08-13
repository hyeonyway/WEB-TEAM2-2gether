package com.dbidding.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RedisOrderWalletTransitionLuaIntegrationTest {
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private DefaultRedisScript<String> script;

    @BeforeEach
    void setUp() {
        if (connectionFactory == null) {
            connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            connectionFactory.afterPropertiesSet();
        }
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/order-wallet-transition.lua"));
        script.setResultType(String.class);
    }

    @AfterAll
    static void tearDown() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test
    void 구매확정은_주문완료_판매자정산_Stream을_함께_전이한다() {
        givenPendingOrder();
        redisTemplate.opsForHash().putAll("wallet:balance:7", Map.of(
                "availableBalance", "10000", "frozenBalance", "0", "walletVersion", "4"
        ));

        String result = execute("1", "COMPLETED", "order.completed.v1", "confirm:100", "hash-confirm");

        assertThat(result).startsWith("ACCEPTED|");
        assertThat(redisTemplate.opsForHash().entries("order:state:10"))
                .containsEntry("status", "COMPLETED").containsEntry("orderVersion", "1")
                .containsEntry("projectionStatus", "PENDING");
        assertThat(redisTemplate.opsForHash().entries("wallet:balance:7"))
                .containsEntry("availableBalance", "60000").containsEntry("walletVersion", "5");
        assertThat(redisTemplate.opsForStream().size("event:timeline")).isEqualTo(1L);

        assertThat(result).endsWith("|false");
        assertThat(execute("1", "COMPLETED", "order.completed.v1", "confirm:100", "hash-confirm"))
                .isEqualTo(result.substring(0, result.length() - "false".length()) + "true");
        assertThat(redisTemplate.opsForStream().size("event:timeline")).isEqualTo(1L);
    }

    @Test
    void 판매자취소는_주문취소_구매자환불_Stream을_함께_전이한다() {
        givenPendingOrder();
        redisTemplate.opsForHash().putAll("wallet:balance:1", Map.of(
                "availableBalance", "20000", "frozenBalance", "0", "walletVersion", "8"
        ));

        execute("7", "CANCELLED", "order.seller-cancelled.v1", "seller-cancel:100", "hash-cancel");

        assertThat(redisTemplate.opsForHash().entries("order:state:10")).containsEntry("status", "CANCELLED");
        assertThat(redisTemplate.opsForHash().entries("wallet:balance:1"))
                .containsEntry("availableBalance", "70000").containsEntry("walletVersion", "9");
        var event = redisTemplate.opsForStream().read(org.springframework.data.redis.connection.stream.StreamOffset.fromStart("event:timeline"))
                .getFirst().getValue();
        assertThat(event).containsEntry("eventType", "order.seller-cancelled.v1")
                .containsEntry("walletUserId", "1").containsEntry("transactionType", "ORDER_CANCEL_REFUND");
    }

    @Test
    void 구매확정_전이는_order_state와_by_order_id와_지갑에_1시간에서_6시간_사이_TTL을_건다() {
        givenPendingOrder();
        redisTemplate.opsForValue().set("order:state:by-order-id:100", "10");
        redisTemplate.opsForHash().putAll("wallet:balance:7", Map.of(
                "availableBalance", "10000", "frozenBalance", "0", "walletVersion", "4"
        ));

        execute("1", "COMPLETED", "order.completed.v1", "confirm:100", "hash-confirm");

        assertThat(redisTemplate.getExpire("order:state:10")).isBetween(3600L, 21600L);
        assertThat(redisTemplate.getExpire("order:state:by-order-id:100")).isBetween(3600L, 21600L);
        assertThat(redisTemplate.getExpire("wallet:balance:7")).isBetween(3600L, 21600L);
    }

    @Test
    void 참여자가_아닌_사용자는_주문과_지갑과_Stream을_변경할_수_없다() {
        givenPendingOrder();
        redisTemplate.opsForHash().putAll("wallet:balance:1", Map.of(
                "availableBalance", "20000", "frozenBalance", "0", "walletVersion", "8"
        ));

        String result = execute("99", "CANCELLED", "order.buyer-cancelled.v1", "buyer-cancel:100", "hash-cancel");

        assertThat(result).isEqualTo("REJECTED|ACCESS_DENIED");
        assertThat(redisTemplate.opsForHash().get("order:state:10", "status")).isEqualTo("PENDING_CONFIRM");
        assertThat(redisTemplate.opsForHash().get("wallet:balance:1", "availableBalance")).isEqualTo("20000");
        assertThat(redisTemplate.opsForStream().size("event:timeline")).isZero();
    }

    @Test
    void 판매자는_구매자취소_명령으로_주문과_지갑을_변경할_수_없다() {
        givenPendingOrder();
        redisTemplate.opsForHash().putAll("wallet:balance:1", Map.of(
                "availableBalance", "20000", "frozenBalance", "0", "walletVersion", "8"
        ));

        String result = execute("7", "CANCELLED", "order.buyer-cancelled.v1", "buyer-cancel:100", "hash-cancel");

        assertThat(result).isEqualTo("REJECTED|ACCESS_DENIED");
        assertThat(redisTemplate.opsForHash().get("order:state:10", "status")).isEqualTo("PENDING_CONFIRM");
        assertThat(redisTemplate.opsForHash().get("wallet:balance:1", "availableBalance")).isEqualTo("20000");
    }

    private void givenPendingOrder() {
        redisTemplate.opsForHash().putAll("order:state:10", Map.of(
                "orderId", "100", "auctionId", "10", "buyerId", "1", "sellerId", "7", "cardName", "리자몽",
                "price", "50000", "status", "PENDING_CONFIRM", "createdAt", "2026-08-12T00:00:00Z", "orderVersion", "0"
        ));
    }

    private String execute(String actorId, String targetStatus, String eventType, String idempotencyKey, String requestHash) {
        return redisTemplate.execute(script, List.of("order:state:10", "wallet:balance:" + ("COMPLETED".equals(targetStatus) ? "7" : "1"),
                        "order:idempotency:100:" + idempotencyKey, "event:timeline", "order:state:by-order-id:100"),
                actorId, targetStatus, eventType, "100", "10", idempotencyKey, requestHash,
                "11111111-1111-1111-1111-111111111111", "2026-08-12T01:00:00Z");
    }
}
