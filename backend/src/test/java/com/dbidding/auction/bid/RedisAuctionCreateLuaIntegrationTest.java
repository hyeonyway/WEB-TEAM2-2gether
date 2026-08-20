package com.dbidding.auction.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.repository.AuctionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RedisAuctionCreateLuaIntegrationTest {
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private AuctionRepository auctionRepository;
    private RedisAuctionCreateExecutor executor;

    @BeforeEach
    void setUp() {
        if (connectionFactory == null) {
            connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            connectionFactory.afterPropertiesSet();
        }
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/auction-create.lua"));
        script.setResultType(String.class);
        DefaultRedisScript<Long> sequenceSyncScript = new DefaultRedisScript<>();
        sequenceSyncScript.setLocation(new ClassPathResource("lua/auction-sequence-sync.lua"));
        sequenceSyncScript.setResultType(Long.class);
        auctionRepository = mock(AuctionRepository.class);
        RedisAuctionSequenceSync sequenceSync = new RedisAuctionSequenceSync(auctionRepository, redisTemplate, sequenceSyncScript);
        executor = new RedisAuctionCreateExecutor(redisTemplate, script,
                Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC), sequenceSync);
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) connectionFactory.destroy();
    }

    @Test
    void 경매_생성은_Redis_상태와_생성_Stream_이벤트를_원자적으로_기록한다() {
        RedisAuctionCreateResult result = executor.execute(command("create-1"));

        assertThat(result.auctionId()).isEqualTo(1);
        assertThat(redisTemplate.opsForHash().entries("auction:state:1"))
                .containsEntry("status", "OPEN")
                .containsEntry("sellerId", "7")
                .containsEntry("cardName", "리자몽")
                .containsEntry("cardSetName", "base")
                .containsEntry("currentPrice", "40000")
                .containsEntry("bidCount", "0")
                .containsEntry("estimatedCloseTime", "2026-08-12T12:00:00Z")
                .containsEntry("estimatedCloseTimeEpochMillis", "1786536000000");
        assertThat(redisTemplate.opsForStream().size("event:timeline")).isEqualTo(1L);
        assertThat(redisTemplate.opsForZSet().range("auction:active:by-close-time", 0, -1)).containsExactly("1");
        assertThat(redisTemplate.opsForZSet().score("auction:ending-window:by-close-time", "1"))
                .isEqualTo(1786535700000D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-bid-count", "1")).isEqualTo(0D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-price", "1")).isEqualTo(40000D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-change-rate", "1")).isEqualTo(0D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-open-time", "1")).isEqualTo(1786492800000D);
        var event = redisTemplate.opsForStream()
                .read(StreamOffset.create("event:timeline", ReadOffset.from("0-0")))
                .getFirst().getValue();
        assertThat(event).containsEntry("eventType", "auction.created.v1")
                .containsEntry("auctionId", "1")
                .containsEntry("sellerId", "7")
                .containsEntry("imagePaths", "/auctions/1.png\n/auctions/2.png");

        assertThat(executor.execute(command("create-1")).auctionId()).isEqualTo(1);
        assertThat(redisTemplate.opsForStream().size("event:timeline")).isEqualTo(1L);
    }

    @Test
    void 이미_존재하는_ID와_충돌하면_기존_경매_state를_덮어쓰지_않는다() {
        // auction:sequence가 어떤 이유로든(예: 카운터 리셋) 이미 활성 경매가 쓰고 있는 ID 바로 앞
        // 값을 가리키면, 다음 INCR이 그 경매와 같은 ID를 내놓는다. auction-create.lua는 이제 HSET
        // 전에 EXISTS를 확인해서, 충돌하면 생성 자체를 거부하고 살아있는 state를 보존한다.
        // 재동기화(findMaxId=5)로도 이번 재시도(INCR 5->6)는 다른 미사용 ID로 넘어가 버려
        // 이 시나리오 자체를 재현하려면 재동기화가 도움이 안 되는 값을 반환하게 한다
        // (예: 복제 지연으로 MySQL 조회가 여전히 뒤처진 경우).
        redisTemplate.opsForHash().putAll("auction:state:5", java.util.Map.of(
                "status", "OPEN", "sellerId", "1", "sequence", "619", "currentPrice", "682841", "bidCount", "624"
        ));
        redisTemplate.opsForHash().putAll("auction:state:6", java.util.Map.of(
                "status", "OPEN", "sellerId", "2", "sequence", "10", "currentPrice", "50000", "bidCount", "3"
        ));
        redisTemplate.opsForValue().set("auction:sequence", "4");
        when(auctionRepository.findMaxId()).thenReturn(4);

        assertThatThrownBy(() -> executor.execute(command("collide-1")))
                .isInstanceOf(AuctionException.class);

        assertThat(redisTemplate.opsForHash().entries("auction:state:5"))
                .containsEntry("sequence", "619")
                .containsEntry("currentPrice", "682841");
        assertThat(redisTemplate.opsForHash().entries("auction:state:6"))
                .containsEntry("sequence", "10")
                .containsEntry("currentPrice", "50000");
    }

    @Test
    void 카운터_드리프트로_인한_ID_COLLISION은_재동기화_후_재시도로_같은_요청_안에서_복구된다() {
        // FLUSHDB 후 부분 복구처럼 카운터가 실제 최대 ID보다 크게(3만큼) 뒤처진 상황을 재현한다.
        // auction:sequence=4에서 첫 INCR은 4->5가 되어 이미 존재하는 auction:state:5와
        // 충돌한다(ID_COLLISION). 단순히 "재시도만" 하면 다음 INCR은 5->6이 되는데, 이 역시
        // 이미 존재하는 auction:state:6과 또 충돌해 버린다(즉, sync()가 실제로 카운터를 앞으로
        // 당기지 못하면 단발성 재시도로는 절대 복구되지 않는다). 여기서 findMaxId()가 MySQL
        // 기준 진짜 최대 ID인 7을 반환하도록 스텁하면, sync()는 첫 INCR로 이미 5가 된 카운터를
        // (target=7 > current=5이므로) 7로 점프시켜야 하고, 그래야만 재시도의 INCR이 7->8이 되어
        // 비어있는 auction:state:8에 안착해 성공한다. 즉 이 테스트는 sync()가 실제로 카운터를
        // 4(또는 5)에서 7로 밀어 올렸을 때만 통과하고, sync() 호출이 빠진 "맨 재시도"로는
        // auctionId=6에서 또 충돌해 예외가 던져지므로 실패한다 — sync()의 캐치업 동작 자체를
        // 검증한다.
        redisTemplate.opsForHash().putAll("auction:state:5", java.util.Map.of(
                "status", "OPEN", "sellerId", "1", "sequence", "619", "currentPrice", "682841", "bidCount", "624"
        ));
        redisTemplate.opsForHash().putAll("auction:state:6", java.util.Map.of(
                "status", "OPEN", "sellerId", "2", "sequence", "10", "currentPrice", "50000", "bidCount", "3"
        ));
        redisTemplate.opsForHash().putAll("auction:state:7", java.util.Map.of(
                "status", "OPEN", "sellerId", "3", "sequence", "2", "currentPrice", "30000", "bidCount", "1"
        ));
        redisTemplate.opsForValue().set("auction:sequence", "4");
        when(auctionRepository.findMaxId()).thenReturn(7);

        RedisAuctionCreateResult result = executor.execute(command("collide-recovers"));

        assertThat(result.auctionId()).isEqualTo(8);
        assertThat(redisTemplate.opsForHash().entries("auction:state:5"))
                .containsEntry("sequence", "619")
                .containsEntry("currentPrice", "682841");
        assertThat(redisTemplate.opsForHash().entries("auction:state:6"))
                .containsEntry("sequence", "10")
                .containsEntry("currentPrice", "50000");
        assertThat(redisTemplate.opsForHash().entries("auction:state:7"))
                .containsEntry("sequence", "2")
                .containsEntry("currentPrice", "30000");
        assertThat(redisTemplate.opsForHash().entries("auction:state:8"))
                .containsEntry("status", "OPEN")
                .containsEntry("sellerId", "7");
    }

    private RedisAuctionCreateCommand command(String idempotencyKey) {
        return new RedisAuctionCreateCommand(7, 10, "리자몽", "base", "10", "JP", "/cards/charizard.png", "리자몽 경매", "설명", "메모", null, "NM", false,
                40_000L, 80_000L, 3_000L, 1_000L, List.of("/auctions/1.png", "/auctions/2.png"),
                Instant.parse("2026-08-12T12:00:00Z"), idempotencyKey, "a".repeat(64));
    }
}
