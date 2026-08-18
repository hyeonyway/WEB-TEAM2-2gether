package com.dbidding.auction.bid.redis;

import com.dbidding.auction.bid.dto.AuctionSeedData;
import com.dbidding.auction.bid.dto.AuctionSeedDbData;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 서로 다른 auctionId가 짧은 시간 안에 동시에 경매 상태 콜드시드로 진입할 때, 각자
 * 개별 쿼리를 던지는 대신 하나의 배치 조회로 묶는다. wallet 쪽 {@code RedisWalletSeedBatchCoordinator}
 * 와 동일한 설계다.
 *
 * <p>{@link com.dbidding.global.concurrent.RedisStateSingleFlight}가 이미 같은 auctionId의
 * 동시 요청을 하나로 걸러주므로, 이 코디네이터에는 항상 auctionId당 최대 1건만 들어온다고
 * 가정한다(그래도 같은 auctionId가 한 배치에 중복 진입해도 안전하도록 방어적으로 짠다).</p>
 */
@Component
@Profile("redis")
public class RedisAuctionSeedBatchCoordinator {
    private final AuctionSeedDataLoader dataLoader;
    private final RedisCardStateReader cardStateReader;
    private final long batchWindowMillis;
    private final int maxBatchSize;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "auction-cold-seed-batch");
        thread.setDaemon(true);
        return thread;
    });

    private final Object lock = new Object();
    private Batch currentBatch;
    private ScheduledFuture<?> scheduledFlush;

    @Autowired
    public RedisAuctionSeedBatchCoordinator(
            AuctionSeedDataLoader dataLoader,
            RedisCardStateReader cardStateReader,
            @Value("${auction.state-seeding.auction-cold-batch.window-ms:5}") long batchWindowMillis,
            @Value("${auction.state-seeding.auction-cold-batch.max-batch-size:200}") int maxBatchSize
    ) {
        this.dataLoader = dataLoader;
        this.cardStateReader = cardStateReader;
        this.batchWindowMillis = batchWindowMillis;
        this.maxBatchSize = maxBatchSize;
    }

    public CompletableFuture<Optional<AuctionSeedData>> requestSeedData(Integer auctionId) {
        Batch earlyFlushTarget = null;
        CompletableFuture<Optional<AuctionSeedData>> future;
        synchronized (lock) {
            if (currentBatch == null) {
                currentBatch = new Batch();
                scheduledFlush = scheduler.schedule(this::flushPending, batchWindowMillis, TimeUnit.MILLISECONDS);
            }
            future = currentBatch.add(auctionId);
            if (currentBatch.size() >= maxBatchSize) {
                earlyFlushTarget = currentBatch;
                currentBatch = null;
                if (scheduledFlush != null) scheduledFlush.cancel(false);
            }
        }
        if (earlyFlushTarget != null) {
            Batch toFlush = earlyFlushTarget;
            scheduler.execute(() -> flush(toFlush));
        }
        return future;
    }

    private void flushPending() {
        Batch batch;
        synchronized (lock) {
            batch = currentBatch;
            currentBatch = null;
        }
        if (batch != null) flush(batch);
    }

    private void flush(Batch batch) {
        try {
            Map<Integer, AuctionSeedDbData> dbData = dataLoader.load(batch.auctionIds());
            if (dbData.isEmpty()) {
                batch.complete(auctionId -> Optional.empty());
                return;
            }
            Map<Integer, CardSnapshot> cards = cardStateReader.getCardSnapshots(dbData.values().stream()
                    .map(data -> data.auction().getItemId())
                    .distinct()
                    .toList());
            batch.complete(auctionId -> Optional.ofNullable(dbData.get(auctionId))
                    .map(data -> new AuctionSeedData(
                            data.auction(), data.leading(), cards.get(data.auction().getItemId()),
                            data.imagePaths(), data.latestBids(), data.recentBids())));
        } catch (Throwable throwable) {
            batch.completeExceptionally(throwable);
        }
    }

    @PreDestroy
    void shutdown() {
        flushPending();
        scheduler.shutdown();
    }

    private static final class Batch {
        private final List<Integer> auctionIds = new ArrayList<>();
        private final Map<Integer, List<CompletableFuture<Optional<AuctionSeedData>>>> waiters = new LinkedHashMap<>();

        synchronized CompletableFuture<Optional<AuctionSeedData>> add(Integer auctionId) {
            CompletableFuture<Optional<AuctionSeedData>> future = new CompletableFuture<>();
            auctionIds.add(auctionId);
            waiters.computeIfAbsent(auctionId, key -> new ArrayList<>()).add(future);
            return future;
        }

        synchronized int size() {
            return auctionIds.size();
        }

        List<Integer> auctionIds() {
            return auctionIds;
        }

        void complete(Function<Integer, Optional<AuctionSeedData>> resolver) {
            waiters.forEach((auctionId, futures) -> {
                Optional<AuctionSeedData> result = resolver.apply(auctionId);
                futures.forEach(future -> future.complete(result));
            });
        }

        void completeExceptionally(Throwable throwable) {
            waiters.values().forEach(futures -> futures.forEach(future -> future.completeExceptionally(throwable)));
        }
    }
}
