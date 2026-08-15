package com.dbidding.auction.stream;

import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import com.dbidding.auction.repository.AuctionTimelineEventRepository;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
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
    private static final String USER_CACHE_KEY = "auction:projection:catchup:user";
    private static final List<AuctionBidEventProjectionStatus> UNPROCESSED_STATUSES =
            List.of(AuctionBidEventProjectionStatus.PENDING, AuctionBidEventProjectionStatus.ERROR);
    /** 이 크기를 넘어서면 다음 쓰기 때 만료된 항목을 정리한다 — TTL이 짧아 대부분 비워진다. */
    private static final int CACHE_CLEANUP_THRESHOLD = 10_000;

    private final StringRedisTemplate redisTemplate;
    private final AuctionTimelineEventRepository eventRepository;
    private final RedisStateSingleFlight singleFlight;
    private final Clock clock;
    private final Duration cacheTtl;

    private volatile CachedResult cached;
    private final ConcurrentHashMap<Integer, CachedResult> auctionCached = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CachedResult> userCached = new ConcurrentHashMap<>();

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
        // 스트림이 비어있다는 건 "새로 안 읽은 이벤트가 없다"는 뜻일 뿐, inbox에 쌓인 PENDING/ERROR가
        // 없다는 뜻이 아니다. 스트림→inbox 적재는 즉시 XDEL로 지워지며 빠르게 끝나고 실제 도메인
        // projection만 느릴 수 있어서, 이 경우에도 아래 PENDING/ERROR 확인은 항상 수행해야 한다.
        boolean latestStreamEntryProcessed = latest == null || latest.isEmpty()
                || eventRepository.findByStreamId(latest.getFirst().getId().getValue())
                        .map(inbox -> inbox.getProjectionStatus() == AuctionBidEventProjectionStatus.PROCESSED)
                        .orElse(false);
        return latestStreamEntryProcessed
                && !eventRepository.existsByProjectionStatus(AuctionBidEventProjectionStatus.PENDING)
                && !eventRepository.existsByProjectionStatus(AuctionBidEventProjectionStatus.ERROR);
    }

    /**
     * 콜드시드 대상 aggregate(경매/주문) 하나의 이벤트 이력만 확인한다. 전역 {@link #isCaughtUp()}과
     * 달리 관계없는 다른 aggregate의 PENDING/ERROR에는 영향받지 않으므로, 한 경매의 지연이 다른
     * 경매의 콜드시드까지 503으로 막는 문제가 없다.
     */
    public boolean isCaughtUpForAuction(Integer auctionId) {
        return isCaughtUpScoped(auctionCached, CACHE_KEY, auctionId, false,
                id -> !eventRepository.existsByAuctionIdAndProjectionStatusIn(id, UNPROCESSED_STATUSES));
    }

    /**
     * {@link #isCaughtUpForAuction(Integer)}와 같은 판정 기준을 쓰되, TTL 캐시를 우회해 항상
     * 최신 MySQL projection 상태를 확인한다. 콜드시드 직전처럼 캐시된 stale {@code true}를 그대로
     * 신뢰하면 안 되는 지점(#535 — 시딩 순간 새 PENDING 이벤트가 막 도착했는데 캐시가 이를 놓쳐,
     * MySQL의 lastBidEventVersion을 신뢰한 채 Redis sequence를 rewind시키는 문제)에서 사용한다.
     * 그래도 singleFlight로 동시 재조회는 하나로 합치고, 결과는 다시 캐시에 반영해 이후의 캐시된
     * 호출자도 이득을 보게 한다.
     */
    public boolean isCaughtUpForAuctionFresh(Integer auctionId) {
        return isCaughtUpScoped(auctionCached, CACHE_KEY, auctionId, true,
                id -> !eventRepository.existsByAuctionIdAndProjectionStatusIn(id, UNPROCESSED_STATUSES));
    }

    /**
     * 콜드시드 대상 userId 하나의 이벤트 이력만 확인한다. 지갑은 auctionId 하나로 스코프를 나눌 수
     * 없다 — {@code WalletStateChangedStreamEvent}는 순수 충전/출금이면 auctionId가 null이고,
     * 한 유저가 동시에 여러 경매에 hold를 걸 수도 있기 때문에 {@link #isCaughtUpForAuction(Integer)}와는
     * 별개의 userId 스코프 차원이 필요하다.
     */
    public boolean isCaughtUpForUser(Integer userId) {
        return isCaughtUpScoped(userCached, USER_CACHE_KEY, userId, false,
                id -> !eventRepository.existsByUserIdAndProjectionStatusIn(id, UNPROCESSED_STATUSES));
    }

    /**
     * {@link #isCaughtUpForUser(Integer)}와 같은 판정 기준을 쓰되, TTL 캐시를 우회해 항상 최신
     * MySQL projection 상태를 확인한다. 지갑 콜드시드 직전처럼 캐시된 stale {@code true}를 신뢰하면
     * 안 되는 지점(#535의 지갑 버전)에서 사용한다.
     */
    public boolean isCaughtUpForUserFresh(Integer userId) {
        return isCaughtUpScoped(userCached, USER_CACHE_KEY, userId, true,
                id -> !eventRepository.existsByUserIdAndProjectionStatusIn(id, UNPROCESSED_STATUSES));
    }

    /**
     * auctionId/userId 스코프 확인 4개(캐시형×2, fresh형×2)가 공유하는 캐시-조회 → singleFlight →
     * 재확인 → 결과캐싱 → 정리 뼈대다. {@code fresh}가 true면 캐시 적중 여부와 무관하게 항상
     * {@code check}를 실행하되, cached/fresh 두 경로가 같은 singleFlight 키를 쓰므로 같은 id에
     * 대한 동시 호출은 여전히 하나의 조회로 합쳐진다.
     */
    private boolean isCaughtUpScoped(
            ConcurrentHashMap<Integer, CachedResult> cache, String keyPrefix, Integer id,
            boolean fresh, Predicate<Integer> check
    ) {
        if (!fresh) {
            CachedResult current = cache.get(id);
            if (current != null && clock.instant().isBefore(current.expiresAt())) return current.caughtUp();
        }
        String key = keyPrefix + ":" + id;
        return singleFlight.execute(key, () -> {
            if (!fresh) {
                CachedResult latest = cache.get(id);
                if (latest != null && clock.instant().isBefore(latest.expiresAt())) return latest.caughtUp();
            }
            boolean result = check.test(id);
            cache.put(id, new CachedResult(result, clock.instant().plus(cacheTtl)));
            evictExpiredIfOversized(cache);
            return result;
        });
    }

    /** TTL로 무의미해진 항목이 key마다 하나씩 쌓여 무한정 커지지 않도록, 크기 임계값을
     * 넘으면 그 시점에 이미 만료된 항목만 정리한다. */
    private void evictExpiredIfOversized(ConcurrentHashMap<Integer, CachedResult> cache) {
        if (cache.size() <= CACHE_CLEANUP_THRESHOLD) return;
        Instant now = clock.instant();
        cache.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private record CachedResult(boolean caughtUp, Instant expiresAt) {}
}
