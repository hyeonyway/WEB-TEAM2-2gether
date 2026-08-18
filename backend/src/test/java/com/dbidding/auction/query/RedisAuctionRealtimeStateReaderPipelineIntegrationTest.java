package com.dbidding.auction.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.dbidding.auction.domain.AuctionSort;
import com.dbidding.auction.dto.AuctionCursorCodec;
import com.dbidding.auction.dto.AuctionSearchRequest;
import com.dbidding.auction.service.AuctionQueryService;
import com.dbidding.auction.service.dblock.DbAuctionQueryService;
import com.dbidding.auction.service.redis.RedisAuctionQueryService;
import com.dbidding.wallet.service.WalletService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RedisAuctionRealtimeStateReaderPipelineIntegrationTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisAuctionRealtimeStateReader reader;

    @BeforeEach
    void setUp() {
        if (connectionFactory == null) {
            connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
            connectionFactory.afterPropertiesSet();
        }
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        reader = new RedisAuctionRealtimeStateReader(redisTemplate);
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) connectionFactory.destroy();
    }

    @Test
    void 여러_state를_pipeline으로_읽고_유효한_경매만_입력순서로_반환한다() {
        redisTemplate.opsForHash().putAll("auction:state:1", validState("경매 1"));
        redisTemplate.opsForHash().putAll("auction:state:2", validState("경매 2"));
        redisTemplate.opsForHash().put("auction:state:4", "status", "OPEN");

        Map<Integer, RedisAuctionRealtimeStateReader.AuctionState> states =
                reader.readAuctionStates(List.of(4, 2, 3, 1));

        assertThat(states.keySet()).containsExactly(2, 1);
        assertThat(states.get(2).auctionName()).isEqualTo("경매 2");
        assertThat(states.get(1).auctionName()).isEqualTo("경매 1");
    }

    @Test
    void 참여한_경매의_bidder_state만_읽어_미참여_key_miss를_만들지_않는다() {
        int userId = 100;
        redisTemplate.opsForSet().add("auction:dashboard:participating:" + userId, "2");
        redisTemplate.opsForHash().putAll("auction:bidder:2:" + userId,
                Map.of("status", "LEADING", "amount", "43000"));
        redisTemplate.getConnectionFactory().getConnection().serverCommands().resetConfigStats();

        Map<Integer, RedisAuctionRealtimeStateReader.MyBidState> states =
                reader.readMyBidStates(List.of(1, 2, 3), userId);

        assertThat(states).containsOnlyKeys(2);
        assertThat(states.get(2).status().name()).isEqualTo("LEADING");
        assertThat(states.get(2).amount()).isEqualTo(43_000L);
        Properties stats = redisTemplate.getConnectionFactory().getConnection().serverCommands().info("stats");
        assertThat(stats.getProperty("keyspace_misses")).isEqualTo("0");
    }

    @Test
    void 익명_목록은_bidder_state를_조회하지_않는다() {
        assertThat(reader.readMyBidStates(List.of(1, 2, 3), null)).isEmpty();
    }

    @Test
    void 로그인_목록은_후보당_state를_한번만_읽고_recent_bids를_조회하지_않는다() {
        for (int auctionId = 1; auctionId <= 20; auctionId++) {
            redisTemplate.opsForHash().putAll("auction:state:" + auctionId, validState("경매 " + auctionId));
            redisTemplate.opsForZSet().add("auction:active:by-bid-count", String.valueOf(auctionId), auctionId);
        }
        int userId = 100;
        redisTemplate.opsForSet().add("auction:dashboard:participating:" + userId, "999");
        redisTemplate.getConnectionFactory().getConnection().serverCommands().resetConfigStats();
        AuctionQueryService service = new AuctionQueryService(mock(DbAuctionQueryService.class));
        RedisAuctionQueryService redisAuctionQueryService = new RedisAuctionQueryService(
                mock(WalletService.class), new AuctionCursorCodec(), reader, null);
        ReflectionTestUtils.setField(service, "redisAuctionQueryService", redisAuctionQueryService);

        var response = service.search(
                userId, new AuctionSearchRequest("", null, AuctionSort.BID_COUNT, null, null, 20));

        assertThat(response.content()).hasSize(20);
        Properties commandStats = redisTemplate.getConnectionFactory().getConnection().serverCommands().info("commandstats");
        assertThat(commandStats.getProperty("cmdstat_hgetall")).startsWith("calls=20,");
        assertThat(commandStats.getProperty("cmdstat_smismember")).startsWith("calls=1,");
        assertThat(commandStats.getProperty("cmdstat_xrevrange")).isNull();
        Properties keyspaceStats = redisTemplate.getConnectionFactory().getConnection().serverCommands().info("stats");
        assertThat(keyspaceStats.getProperty("keyspace_misses")).isEqualTo("0");
    }

    private Map<String, String> validState(String auctionName) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("status", "OPEN");
        fields.put("sellerId", "2");
        fields.put("itemId", "10");
        fields.put("cardName", "리자몽");
        fields.put("cardSetName", "base");
        fields.put("cardPsaGrade", "10");
        fields.put("cardLanguage", "JP");
        fields.put("cardThumbnailUrl", "/cards/charizard.png");
        fields.put("auctionName", auctionName);
        fields.put("description", "설명");
        fields.put("psaVerified", "false");
        fields.put("startPrice", "40000");
        fields.put("currentPrice", "43000");
        fields.put("bidIncrement", "3000");
        fields.put("bidCount", "1");
        fields.put("deliveryFee", "3000");
        fields.put("imagePaths", "/auctions/1.png");
        fields.put("openTime", "2026-08-10T00:00:00Z");
        fields.put("closeTime", "2026-08-10T01:00:00Z");
        return fields;
    }
}
