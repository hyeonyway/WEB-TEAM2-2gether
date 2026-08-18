package com.dbidding.auction.service.redis;

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
class RedisAuctionCloseRequestLuaIntegrationTest {
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate template;
    private DefaultRedisScript<String> script;

    @BeforeEach
    void setUp() {
        if (connectionFactory == null) {
            connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            connectionFactory.afterPropertiesSet();
        }
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        template.getConnectionFactory().getConnection().serverCommands().flushDb();
        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/auction-close-request.lua"));
        script.setResultType(String.class);
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) connectionFactory.destroy();
    }

    @Test
    void 마감_승인은_경매종료와_낙찰자_capture를_원자적으로_처리하고_실시간_payload를_반환한다() {
        template.opsForHash().putAll("auction:state:11", Map.ofEntries(
                Map.entry("status", "OPEN"), Map.entry("sellerId", "7"), Map.entry("itemId", "10"),
                Map.entry("cardName", "리자몽"), Map.entry("cardPsaGrade", "10"), Map.entry("cardLanguage", "JP"),
                Map.entry("cardThumbnailUrl", "/thumb.png"), Map.entry("startPrice", "40000"),
                Map.entry("currentPrice", "50000"), Map.entry("bidIncrement", "3000"), Map.entry("bidCount", "3"),
                Map.entry("highestBidderId", "2"), Map.entry("highestHoldAmount", "50000")
        ));
        template.opsForHash().putAll("wallet:balance:2", Map.of(
                "availableBalance", "50000", "frozenBalance", "50000", "walletVersion", "5"));
        template.opsForHash().put("wallet:hold:11:2", "amount", "50000");
        template.opsForZSet().add("auction:active:by-close-time", "11", 1786496400000D);
        template.opsForZSet().add("auction:ending-window:by-close-time", "11", 1786496100000D);
        template.opsForZSet().add("auction:active:by-bid-count", "11", 3D);
        template.opsForZSet().add("auction:active:by-price", "11", 50000D);
        template.opsForZSet().add("auction:active:by-change-rate", "11", 2500D);
        template.opsForZSet().add("auction:active:by-open-time", "11", 1786490000000D);

        List<String> keys = List.of("auction:state:11", "event:timeline", "auction:ending-window:by-close-time",
                "auction:active:by-bid-count", "auction:active:by-price", "auction:active:by-change-rate", "auction:active:by-open-time");
        String result = execute(keys);

        assertThat(result).isEqualTo("ACCEPTED|2|50000|7|10|리자몽|10|JP|/thumb.png|40000|50000|3000|3|50000|0|6");
        assertThat(template.opsForHash().entries("auction:state:11"))
                .containsEntry("status", "ENDED").containsEntry("closeRequestedAt", "2026-08-12T01:00:00Z");
        assertThat(template.opsForHash().entries("wallet:balance:2"))
                .containsEntry("availableBalance", "50000").containsEntry("frozenBalance", "0")
                .containsEntry("walletVersion", "6");
        assertThat(template.hasKey("wallet:hold:11:2")).isFalse();
        assertThat(template.opsForStream().size("event:timeline")).isEqualTo(1L);
        assertThat(template.opsForZSet().score("auction:active:by-close-time", "11")).isNull();
        assertThat(template.opsForZSet().score("auction:ending-window:by-close-time", "11")).isNull();
        assertThat(template.opsForZSet().score("auction:active:by-bid-count", "11")).isNull();
        assertThat(template.opsForZSet().score("auction:active:by-price", "11")).isNull();
        assertThat(template.opsForZSet().score("auction:active:by-change-rate", "11")).isNull();
        assertThat(template.opsForZSet().score("auction:active:by-open-time", "11")).isNull();
        assertThat(execute(keys)).isEqualTo("REPLAYED");
        assertThat(template.opsForStream().size("event:timeline")).isEqualTo(1L);
    }

    @Test
    void 큰_낙찰액과_지갑_버전도_Hash와_응답에_정수_문자열로_유지한다() {
        template.opsForHash().putAll("auction:state:11", Map.ofEntries(
                Map.entry("status", "OPEN"), Map.entry("sellerId", "7"), Map.entry("itemId", "10"),
                Map.entry("cardName", "리자몽"), Map.entry("startPrice", "800000000000"),
                Map.entry("currentPrice", "900000000000"), Map.entry("bidIncrement", "100000000000"),
                Map.entry("bidCount", "3"), Map.entry("highestBidderId", "2"),
                Map.entry("highestHoldAmount", "900000000000")
        ));
        template.opsForHash().putAll("wallet:balance:2", Map.of(
                "availableBalance", "100000000000", "frozenBalance", "900000000000",
                "walletVersion", "99999999999999"));
        template.opsForHash().put("wallet:hold:11:2", "amount", "900000000000");
        template.opsForZSet().add("auction:active:by-close-time", "11", 1786496400000D);
        List<String> keys = closeKeys();

        String result = execute(keys);

        assertThat(result).doesNotContain("e+").doesNotContain("E+")
                .contains("|900000000000|").endsWith("|100000000000|0|100000000000000");
        assertThat(template.opsForHash().entries("wallet:balance:2"))
                .containsEntry("availableBalance", "100000000000")
                .containsEntry("frozenBalance", "0")
                .containsEntry("walletVersion", "100000000000000");
        assertThat(template.hasKey("wallet:hold:11:2")).isFalse();
    }

    @Test
    void 낙찰액이_정책_상한을_넘으면_경매와_지갑을_변경하지_않는다() {
        template.opsForHash().putAll("auction:state:11", Map.ofEntries(
                Map.entry("status", "OPEN"), Map.entry("sellerId", "7"), Map.entry("itemId", "10"),
                Map.entry("cardName", "리자몽"), Map.entry("startPrice", "1000000000000"),
                Map.entry("currentPrice", "1000000000001"), Map.entry("bidIncrement", "1"),
                Map.entry("bidCount", "1"), Map.entry("highestBidderId", "2"),
                Map.entry("highestHoldAmount", "1000000000001")
        ));
        template.opsForHash().putAll("wallet:balance:2", Map.of(
                "availableBalance", "0", "frozenBalance", "1000000000001", "walletVersion", "5"));
        template.opsForHash().put("wallet:hold:11:2", "amount", "1000000000001");

        String result = execute(closeKeys());

        assertThat(result).isEqualTo("REJECTED|AMOUNT_LIMIT_EXCEEDED");
        assertThat(template.opsForHash().get("auction:state:11", "status")).isEqualTo("OPEN");
        assertThat(template.opsForHash().entries("wallet:balance:2"))
                .containsEntry("frozenBalance", "1000000000001").containsEntry("walletVersion", "5");
        assertThat(template.hasKey("wallet:hold:11:2")).isTrue();
        assertThat(template.opsForStream().size("event:timeline")).isZero();
    }

    private List<String> closeKeys() {
        return List.of("auction:state:11", "event:timeline", "auction:ending-window:by-close-time",
                "auction:active:by-bid-count", "auction:active:by-price", "auction:active:by-change-rate",
                "auction:active:by-open-time");
    }

    private String execute(List<String> keys) {
        return template.execute(script, keys, "11", "2026-08-12T01:00:00Z", "1786496400000", "1000000000000");
    }
}
