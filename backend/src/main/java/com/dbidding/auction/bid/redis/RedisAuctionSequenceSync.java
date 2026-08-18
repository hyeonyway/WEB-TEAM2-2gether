package com.dbidding.auction.bid.redis;

import com.dbidding.auction.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.List;

/**
 * 기동 시 경매 ID 발급용 {@code auction:sequence} 카운터를 MySQL의 실제 최대 경매 ID
 * 이상으로 맞춘다. Redis 장애 복구(RDB/AOF 스냅샷 시차), FLUSHDB 후 재구성, 콜드시드 등으로
 * 이 카운터가 실제 최대 ID보다 뒤처지면, 다음 {@code INCR}이 이미 사용 중인 ID를 다시
 * 내놓아 활성 경매와 충돌한다({@code auction-create.lua}의 EXISTS 가드가 그 결과를
 * 막아주지만, 카운터 자체가 따라잡을 때까지 신규 경매 생성이 계속 거부된다).
 */
@Configuration
@Profile("redis")
@RequiredArgsConstructor
public class RedisAuctionSequenceSync {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RedisAuctionSequenceSync.class);
    private static final String SEQUENCE_KEY = "auction:sequence";

    private final AuctionRepository auctionRepository;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> auctionSequenceSyncScript;

    @Bean("redisAuctionSequenceSyncRunner")
    ApplicationRunner redisAuctionSequenceSyncRunner() {
        return arguments -> {
            Integer maxId = auctionRepository.findMaxId();
            if (maxId == null) return;
            Long synced = redisTemplate.execute(auctionSequenceSyncScript, List.of(SEQUENCE_KEY), String.valueOf(maxId));
            if (Long.valueOf(1L).equals(synced)) {
                log.info("event=auction.sequence.sync.applied targetId={}", maxId);
            }
        };
    }
}
