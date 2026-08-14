package com.dbidding.auction.stream;

import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import com.dbidding.auction.repository.AuctionTimelineEventRepository;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis state miss를 MySQL projection으로 복원하기 전에 Stream 소비 완료 여부를 확인한다.
 *
 * <p>이 확인은 엔티티(경매/지갑 등)와 무관한 전역 상태 하나뿐이라, 서로 다른 엔티티가 동시에
 * 콜드미스 나더라도 매번 다시 조회할 필요가 없다. 짧은 TTL로 캐싱하고, 캐시가 만료된 순간에도
 * {@link RedisStateSingleFlight}로 동시 재조회를 하나로 합친다.</p>
 */
@Component
@Profile("redis")
public class RedisProjectionCatchUpVerifier {
    private static final String STREAM_KEY = "event:timeline";
    private static final String CACHE_KEY = "auction:projection:catchup";

    private final StringRedisTemplate redisTemplate;
    private final AuctionTimelineEventRepository eventRepository;
    private final RedisStateSingleFlight singleFlight;
    private final Clock clock;
    private final Duration cacheTtl;

    private volatile CachedResult cached;

    @Autowired
    public RedisProjectionCatchUpVerifier(
            StringRedisTemplate redisTemplate,
            AuctionTimelineEventRepository eventRepository,
            RedisStateSingleFlight singleFlight,
            Clock clock,
            @Value("${auction.catchup-verification.cache-ttl:PT0.5S}") Duration cacheTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.eventRepository = eventRepository;
        this.singleFlight = singleFlight;
        this.clock = clock;
        this.cacheTtl = cacheTtl;
    }

    public boolean isCaughtUp() {
        CachedResult current = cached;
        if (current != null && clock.instant().isBefore(current.expiresAt())) return current.caughtUp();
        return singleFlight.execute(CACHE_KEY, () -> {
            CachedResult latest = cached;
            if (latest != null && clock.instant().isBefore(latest.expiresAt())) return latest.caughtUp();
            boolean result = checkCaughtUp();
            cached = new CachedResult(result, clock.instant().plus(cacheTtl));
            return result;
        });
    }

    private boolean checkCaughtUp() {
        List<MapRecord<String, Object, Object>> latest = redisTemplate.opsForStream().reverseRange(
                STREAM_KEY, org.springframework.data.domain.Range.unbounded(), Limit.limit().count(1)
        );
        if (latest == null || latest.isEmpty()) return true;
        String streamId = latest.getFirst().getId().getValue();
        return eventRepository.findByStreamId(streamId)
                .map(inbox -> inbox.getProjectionStatus() == AuctionBidEventProjectionStatus.PROCESSED)
                .orElse(false)
                && !eventRepository.existsByProjectionStatus(AuctionBidEventProjectionStatus.PENDING)
                && !eventRepository.existsByProjectionStatus(AuctionBidEventProjectionStatus.ERROR);
    }

    private record CachedResult(boolean caughtUp, Instant expiresAt) {}
}
