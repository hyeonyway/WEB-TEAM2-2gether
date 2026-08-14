package com.dbidding.wallet.service;

import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;
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
 * 서로 다른 userId가 짧은 시간 안에 동시에 지갑 콜드시드로 진입할 때, 각자 개별
 * 쿼리를 던지는 대신 하나의 배치 조회({@code WHERE user_id IN (...)})로 묶는다.
 *
 * <p>{@link com.dbidding.global.concurrent.RedisStateSingleFlight}가 이미 같은 userId의
 * 동시 요청을 하나로 걸러주므로, 이 코디네이터에는 항상 userId당 최대 1건만 들어온다고
 * 가정한다(그래도 같은 userId가 한 배치에 중복 진입해도 안전하도록 방어적으로 짠다).</p>
 */
@Component
@Profile("redis")
public class RedisWalletSeedBatchCoordinator {
    private final WalletHoldRepository walletHoldRepository;
    private final WalletRepository walletRepository;
    private final long batchWindowMillis;
    private final int maxBatchSize;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "wallet-cold-seed-batch");
        thread.setDaemon(true);
        return thread;
    });

    private final Object lock = new Object();
    private Batch currentBatch;
    private ScheduledFuture<?> scheduledFlush;

    @Autowired
    public RedisWalletSeedBatchCoordinator(
            WalletHoldRepository walletHoldRepository,
            WalletRepository walletRepository,
            @Value("${auction.state-seeding.wallet-cold-batch.window-ms:5}") long batchWindowMillis,
            @Value("${auction.state-seeding.wallet-cold-batch.max-batch-size:200}") int maxBatchSize
    ) {
        this.walletHoldRepository = walletHoldRepository;
        this.walletRepository = walletRepository;
        this.batchWindowMillis = batchWindowMillis;
        this.maxBatchSize = maxBatchSize;
    }

    public CompletableFuture<Optional<WalletSeedData>> requestSeedData(Integer userId) {
        Batch earlyFlushTarget = null;
        CompletableFuture<Optional<WalletSeedData>> future;
        synchronized (lock) {
            if (currentBatch == null) {
                currentBatch = new Batch();
                scheduledFlush = scheduler.schedule(this::flushPending, batchWindowMillis, TimeUnit.MILLISECONDS);
            }
            future = currentBatch.add(userId);
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
            Map<Integer, WalletSeedData> resolved = WalletSeedData.resolveBatch(batch.userIds(), walletHoldRepository, walletRepository);
            batch.complete(userId -> Optional.ofNullable(resolved.get(userId)));
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
        private final List<Integer> userIds = new ArrayList<>();
        private final Map<Integer, List<CompletableFuture<Optional<WalletSeedData>>>> waiters = new LinkedHashMap<>();

        synchronized CompletableFuture<Optional<WalletSeedData>> add(Integer userId) {
            CompletableFuture<Optional<WalletSeedData>> future = new CompletableFuture<>();
            userIds.add(userId);
            waiters.computeIfAbsent(userId, key -> new ArrayList<>()).add(future);
            return future;
        }

        synchronized int size() {
            return userIds.size();
        }

        List<Integer> userIds() {
            return userIds;
        }

        void complete(Function<Integer, Optional<WalletSeedData>> resolver) {
            waiters.forEach((userId, futures) -> {
                Optional<WalletSeedData> result = resolver.apply(userId);
                futures.forEach(future -> future.complete(result));
            });
        }

        void completeExceptionally(Throwable throwable) {
            waiters.values().forEach(futures -> futures.forEach(future -> future.completeExceptionally(throwable)));
        }
    }
}
