package com.dbidding.auction.service;

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

        String result = template.execute(script, List.of("auction:state:11", "event:timeline"),
                "11", "2026-08-12T01:00:00Z", "1786496400000");

        assertThat(result).isEqualTo("ACCEPTED|2|50000|7|10|리자몽|10|JP|/thumb.png|40000|50000|3000|3|50000|0|6");
        assertThat(template.opsForHash().entries("auction:state:11"))
                .containsEntry("status", "ENDED").containsEntry("closeRequestedAt", "2026-08-12T01:00:00Z");
        assertThat(template.opsForHash().entries("wallet:balance:2"))
                .containsEntry("availableBalance", "50000").containsEntry("frozenBalance", "0")
                .containsEntry("walletVersion", "6");
        assertThat(template.hasKey("wallet:hold:11:2")).isFalse();
        assertThat(template.opsForStream().size("event:timeline")).isEqualTo(1L);
        assertThat(template.execute(script, List.of("auction:state:11", "event:timeline"),
                "11", "2026-08-12T01:00:00Z", "1786496400000")).isEqualTo("REPLAYED");
        assertThat(template.opsForStream().size("event:timeline")).isEqualTo(1L);
    }
}
