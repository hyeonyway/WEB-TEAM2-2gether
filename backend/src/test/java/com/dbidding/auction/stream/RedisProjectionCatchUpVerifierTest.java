package com.dbidding.auction.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import com.dbidding.auction.domain.AuctionTimelineEvent;
import com.dbidding.auction.repository.AuctionTimelineEventRepository;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisProjectionCatchUpVerifierTest {
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
    private final AuctionTimelineEventRepository eventRepository = mock(AuctionTimelineEventRepository.class);
    private final RedisStateSingleFlight singleFlight = new RedisStateSingleFlight();
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-08-14T00:00:00Z"));
    private final Clock clock = mock(Clock.class);

    private RedisProjectionCatchUpVerifier verifier(Duration cacheTtl) {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(clock.instant()).thenAnswer(invocation -> now.get());
        return new RedisProjectionCatchUpVerifier(redisTemplate, eventRepository, singleFlight, clock, cacheTtl);
    }

    @SuppressWarnings("unchecked")
    private void stubLatestProcessed() {
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of("5-0"));
        when(streamOperations.reverseRange(eq("event:timeline"), any(), any())).thenReturn(List.of(record));
        AuctionTimelineEvent inbox = mock(AuctionTimelineEvent.class);
        when(inbox.getProjectionStatus()).thenReturn(AuctionBidEventProjectionStatus.PROCESSED);
        when(eventRepository.findByStreamId("5-0")).thenReturn(Optional.of(inbox));
        when(eventRepository.existsByProjectionStatus(AuctionBidEventProjectionStatus.PENDING)).thenReturn(false);
        when(eventRepository.existsByProjectionStatus(AuctionBidEventProjectionStatus.ERROR)).thenReturn(false);
    }

    @Test
    void TTL_이내_반복_호출은_캐시된_값을_재사용하고_추가_조회를_하지_않는다() {
        stubLatestProcessed();
        RedisProjectionCatchUpVerifier verifier = verifier(Duration.ofMillis(500));

        assertThat(verifier.isCaughtUp()).isTrue();
        assertThat(verifier.isCaughtUp()).isTrue();

        verify(eventRepository, times(1)).findByStreamId("5-0");
    }

    @Test
    void TTL이_지나면_캐시를_쓰지_않고_다시_확인한다() {
        stubLatestProcessed();
        RedisProjectionCatchUpVerifier verifier = verifier(Duration.ofMillis(500));

        assertThat(verifier.isCaughtUp()).isTrue();
        now.set(now.get().plusMillis(600));
        assertThat(verifier.isCaughtUp()).isTrue();

        verify(eventRepository, times(2)).findByStreamId("5-0");
    }

    @Test
    void PENDING_이벤트가_있으면_캐치업되지_않은_것으로_판단한다() {
        stubLatestProcessed();
        when(eventRepository.existsByProjectionStatus(AuctionBidEventProjectionStatus.PENDING)).thenReturn(true);
        RedisProjectionCatchUpVerifier verifier = verifier(Duration.ofMillis(500));

        assertThat(verifier.isCaughtUp()).isFalse();
    }

    @Test
    void 서로_다른_엔티티가_동시에_콜드미스_나도_실제_조회는_한_번만_수행된다() throws Exception {
        stubLatestProcessed();
        RedisProjectionCatchUpVerifier verifier = verifier(Duration.ofMillis(500));
        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return verifier.isCaughtUp();
            }));
        }
        ready.await();
        start.countDown();
        for (Future<Boolean> future : futures) {
            assertThat(future.get(5, TimeUnit.SECONDS)).isTrue();
        }
        pool.shutdown();

        verify(eventRepository, times(1)).findByStreamId("5-0");
    }
}
