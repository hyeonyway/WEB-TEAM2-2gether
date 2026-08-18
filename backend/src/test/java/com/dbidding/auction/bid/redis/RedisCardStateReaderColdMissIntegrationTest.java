package com.dbidding.auction.bid.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.repository.CardMetadataRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * {@link RedisAuctionSeedBatchCoordinator}의 전용 스케줄러 스레드에는 @Transactional 경계가 없다.
 * 카드 Redis 캐시까지 미스인 콜드 상태에서 {@link RedisCardStateReader#getCardSnapshots}가
 * JPA로 조회한 {@code CardMetadata}는 repository 메서드 자체의 짧은 트랜잭션이 끝난 뒤 detached되므로,
 * LAZY 연관관계인 cardSet에 접근하면 LazyInitializationException이 나야 정상이다. 이 테스트는
 * @DataJpaTest의 기본 트랜잭션 래핑을 꺼서 그 상황을 그대로 재현하고, cardSet을 JOIN FETCH하는
 * 배치 조회로 안전하게 동작하는지 검증한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.sql.init.mode=always")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RedisCardStateReaderColdMissIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("dbidding");

    @Autowired
    private CardMetadataRepository cardMetadataRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 트랜잭션_밖에서_카드_캐시_미스여도_LazyInitializationException_없이_snapshot을_만든다() {
        Integer cardSetId = insertCardSet("cold-miss-test-set");
        Integer cardId = insertCardMetadata(cardSetId, "cold-miss-test-card");
        try {
            StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
            @SuppressWarnings("unchecked")
            HashOperations<String, Object, Object> hashes = Mockito.mock(HashOperations.class);
            Mockito.when(redisTemplate.opsForHash()).thenReturn(hashes);
            Mockito.when(hashes.entries(Mockito.anyString())).thenReturn(Map.of());
            RedisCardStateReader reader = new RedisCardStateReader(redisTemplate, cardMetadataRepository, 86_400, 3_600);

            Map<Integer, CardSnapshot> snapshots = reader.getCardSnapshots(List.of(cardId));

            assertThat(snapshots.get(cardId).name()).isEqualTo("cold-miss-test-card");
            assertThat(snapshots.get(cardId).setName()).isEqualTo("cold-miss-test-set");
        } finally {
            jdbcTemplate.update("DELETE FROM card_metadata WHERE id = ?", cardId);
            jdbcTemplate.update("DELETE FROM card_sets WHERE id = ?", cardSetId);
        }
    }

    private Integer insertCardSet(String name) {
        Number generatedId = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("card_sets")
                .usingColumns("name")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(new MapSqlParameterSource().addValue("name", name));
        return generatedId.intValue();
    }

    private Integer insertCardMetadata(Integer cardSetId, String name) {
        Number generatedId = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("card_metadata")
                .usingColumns("card_set_id", "name")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(new MapSqlParameterSource()
                        .addValue("card_set_id", cardSetId)
                        .addValue("name", name));
        return generatedId.intValue();
    }
}
