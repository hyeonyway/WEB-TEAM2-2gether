package com.dbidding.auction.bid;

import com.dbidding.auction.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.List;

/**
 * 경매 ID 발급용 {@code auction:sequence} 카운터를 MySQL의 실제 최대 경매 ID
 * 이상으로 맞춘다. Redis 장애 복구(RDB/AOF 스냅샷 시차), FLUSHDB 후 재구성, 콜드시드 등으로
 * 이 카운터가 실제 최대 ID보다 뒤처지면, 다음 {@code INCR}이 이미 사용 중인 ID를 다시
 * 내놓아 활성 경매와 충돌한다({@code auction-create.lua}의 EXISTS 가드가 그 결과를
 * 막아주지만, 카운터 자체가 따라잡을 때까지 신규 경매 생성이 계속 거부된다).
 *
 * <p>이 재동기화는 세 경로에서 재사용된다: 기동 시 1회({@link #redisAuctionSequenceSyncRunner()}),
 * 런타임 드리프트에 대한 백업 안전망으로 주기적으로({@link #syncPeriodically()}), 그리고
 * {@link RedisAuctionCreateExecutor}가 {@code ID_COLLISION}을 감지했을 때 재시도 전
 * 즉시(reactively) — 재시작 없이도 다음 생성 시도가 성공하도록 한다.
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
        return arguments -> sync();
    }

    /**
     * 기동 시 동기화 이후에도 Redis 장애 복구나 운영자 실수(FLUSHDB 등)로 카운터가
     * 다시 뒤처질 수 있으므로, 사람의 재시작 없이도 스스로 따라잡도록 주기적으로 재확인한다.
     * 여러 인스턴스가 동시에 실행해도 결과가 같은 멱등 연산이지만, 불필요한 중복 실행을
     * 줄이기 위해 리더에게만 락을 준다.
     */
    @Scheduled(fixedDelayString = "${auction.sequence-sync.fixed-delay-ms:300000}")
    @SchedulerLock(
            name = "auction-sequence-sync",
            lockAtLeastFor = "PT10S",
            lockAtMostFor = "PT1M"
    )
    void syncPeriodically() {
        sync();
    }

    /**
     * MySQL 기준 실제 최대 경매 ID로 {@code auction:sequence} 카운터를 맞춘다.
     * 카운터가 이미 그 값 이상이면 아무 것도 하지 않는다({@code auction-sequence-sync.lua}의
     * "target > current" 가드로 뒤로 되돌리지 않음을 보장).
     *
     * @return 카운터를 실제로 올렸으면 {@code true}
     */
    boolean sync() {
        Integer maxId = auctionRepository.findMaxId();
        if (maxId == null) return false;
        Long synced = redisTemplate.execute(auctionSequenceSyncScript, List.of(SEQUENCE_KEY), String.valueOf(maxId));
        boolean applied = Long.valueOf(1L).equals(synced);
        if (applied) {
            log.info("event=auction.sequence.sync.applied targetId={}", maxId);
        }
        return applied;
    }
}
