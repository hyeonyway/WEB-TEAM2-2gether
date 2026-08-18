package com.dbidding.auction.stream;

import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import com.dbidding.auction.repository.AuctionTimelineEventRepository;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final List<AuctionBidEventProjectionStatus> UNPROCESSED_STATUSES =
            List.of(AuctionBidEventProjectionStatus.PENDING, AuctionBidEventProjectionStatus.ERROR);
    /** 이 크기를 넘어서면 다음 쓰기 때 만료된 항목을 정리한다 — TTL이 짧아 대부분 비워진다. */
    private static final int AUCTION_CACHE_CLEANUP_THRESHOLD = 10_000;

    private final StringRedisTemplate redisTemplate;
    private final AuctionTimelineEventRepository eventRepository;
    private final RedisStateSingleFlight singleFlight;
    private final Clock clock;
    private final Duration cacheTtl;

    private volatile CachedResult cached;
    private final ConcurrentHashMap<Integer, CachedResult> auctionCached = new ConcurrentHashMap<>();

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

    /**
     * 콜드시드 대상 aggregate(경매/주문) 하나의 이벤트 이력만 확인한다. 전역 {@link #isCaughtUp()}과
     * 달리 관계없는 다른 aggregate의 PENDING/ERROR에는 영향받지 않으므로, 한 경매의 지연이 다른
     * 경매의 콜드시드까지 503으로 막는 문제가 없다.
     */
    public boolean isCaughtUp(Integer auctionId) {
        CachedResult current = auctionCached.get(auctionId);
        if (current != null && clock.instant().isBefore(current.expiresAt())) return current.caughtUp();
        String key = CACHE_KEY + ":" + auctionId;
        return singleFlight.execute(key, () -> {
            CachedResult latest = auctionCached.get(auctionId);
            if (latest != null && clock.instant().isBefore(latest.expiresAt())) return latest.caughtUp();
            boolean result = checkCaughtUp(auctionId);
            auctionCached.put(auctionId, new CachedResult(result, clock.instant().plus(cacheTtl)));
            evictExpiredIfOversized();
            return result;
        });
    }

    private boolean checkCaughtUp(Integer auctionId) {
        return !eventRepository.existsByAuctionIdAndProjectionStatusIn(auctionId, UNPROCESSED_STATUSES);
    }

    /** TTL로 무의미해진 항목이 auctionId마다 하나씩 쌓여 무한정 커지지 않도록, 크기 임계값을
     * 넘으면 그 시점에 이미 만료된 항목만 정리한다. */
    private void evictExpiredIfOversized() {
        if (auctionCached.size() <= AUCTION_CACHE_CLEANUP_THRESHOLD) return;
        Instant now = clock.instant();
        auctionCached.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private record CachedResult(boolean caughtUp, Instant expiresAt) {}
}
