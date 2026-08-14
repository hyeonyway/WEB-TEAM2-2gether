package com.dbidding.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.wallet.repository.WalletBootstrapRow;
import com.dbidding.wallet.repository.WalletHeldHoldRow;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RedisWalletSeedBatchCoordinatorTest {
    private final WalletHoldRepository walletHoldRepository = mock(WalletHoldRepository.class);
    private final WalletRepository walletRepository = mock(WalletRepository.class);

    private WalletBootstrapRow wallet(Integer userId, long point, long frozen, long version) {
        WalletBootstrapRow row = mock(WalletBootstrapRow.class);
        when(row.getUserId()).thenReturn(userId);
        when(row.getPoint()).thenReturn(point);
        when(row.getFrozenBalance()).thenReturn(frozen);
        when(row.getProjectionVersion()).thenReturn(version);
        return row;
    }

    @Test
    void 서로_다른_userId가_짧은_윈도우_안에_동시에_요청하면_배치_조회는_한_번만_나간다() throws Exception {
        int userCount = 20;
        List<Integer> userIds = new ArrayList<>();
        List<WalletBootstrapRow> wallets = new ArrayList<>();
        for (int i = 1; i <= userCount; i++) {
            userIds.add(i);
            wallets.add(wallet(i, 100_000L, 0L, 1L));
        }
        when(walletHoldRepository.findHeldRowsForUsers(anyList())).thenReturn(List.of());
        when(walletRepository.findBootstrapRowsForUsers(anyList())).thenReturn(wallets);
        RedisWalletSeedBatchCoordinator coordinator = new RedisWalletSeedBatchCoordinator(
                walletHoldRepository, walletRepository, 50, 200
        );

        ExecutorService pool = Executors.newFixedThreadPool(userCount);
        CountDownLatch ready = new CountDownLatch(userCount);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Optional<WalletSeedData>>> futures = new ArrayList<>();
        for (Integer userId : userIds) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return coordinator.requestSeedData(userId).join();
            }, pool));
        }
        ready.await();
        start.countDown();
        for (CompletableFuture<Optional<WalletSeedData>> future : futures) {
            assertThat(future.get(5, TimeUnit.SECONDS)).isPresent();
        }
        pool.shutdown();

        verify(walletHoldRepository, times(1)).findHeldRowsForUsers(anyList());
        verify(walletRepository, times(1)).findBootstrapRowsForUsers(anyList());
    }

    @Test
    void 배치_크기에_도달하면_윈도우를_기다리지_않고_즉시_flush한다() throws Exception {
        int maxBatchSize = 5;
        List<WalletBootstrapRow> wallets = new ArrayList<>();
        for (int i = 1; i <= maxBatchSize; i++) wallets.add(wallet(i, 100_000L, 0L, 1L));
        when(walletHoldRepository.findHeldRowsForUsers(anyList())).thenReturn(List.of());
        when(walletRepository.findBootstrapRowsForUsers(anyList())).thenReturn(wallets);
        // window를 아주 길게 둬서, 조기 flush가 없다면 이 테스트는 타임아웃난다.
        RedisWalletSeedBatchCoordinator coordinator = new RedisWalletSeedBatchCoordinator(
                walletHoldRepository, walletRepository, 10_000, maxBatchSize
        );

        List<CompletableFuture<Optional<WalletSeedData>>> futures = new ArrayList<>();
        for (int i = 1; i <= maxBatchSize; i++) {
            futures.add(coordinator.requestSeedData(i));
        }
        for (CompletableFuture<Optional<WalletSeedData>> future : futures) {
            assertThat(future.get(2, TimeUnit.SECONDS)).isPresent();
        }

        verify(walletHoldRepository, times(1)).findHeldRowsForUsers(anyList());
        verify(walletRepository, times(1)).findBootstrapRowsForUsers(anyList());
    }

    @Test
    void 지갑_row가_없는_유저는_빈_값으로_완료된다() {
        when(walletHoldRepository.findHeldRowsForUsers(anyList())).thenReturn(List.of());
        when(walletRepository.findBootstrapRowsForUsers(anyList())).thenReturn(List.of());
        RedisWalletSeedBatchCoordinator coordinator = new RedisWalletSeedBatchCoordinator(
                walletHoldRepository, walletRepository, 5, 200
        );

        Optional<WalletSeedData> result = coordinator.requestSeedData(1).join();

        assertThat(result).isEmpty();
    }

    @Test
    void flush_중_예외가_발생하면_대기중이던_모든_호출자가_hang_없이_실패한다() {
        when(walletHoldRepository.findHeldRowsForUsers(anyList())).thenThrow(new IllegalStateException("DB down"));
        RedisWalletSeedBatchCoordinator coordinator = new RedisWalletSeedBatchCoordinator(
                walletHoldRepository, walletRepository, 5, 200
        );

        CompletableFuture<Optional<WalletSeedData>> first = coordinator.requestSeedData(1);
        CompletableFuture<Optional<WalletSeedData>> second = coordinator.requestSeedData(2);

        assertThatThrownBy(first::join).isInstanceOf(CompletionException.class);
        assertThatThrownBy(second::join).isInstanceOf(CompletionException.class);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private WalletHeldHoldRow hold(Integer userId, Integer auctionId, long amount) {
        WalletHeldHoldRow row = mock(WalletHeldHoldRow.class);
        when(row.getUserId()).thenReturn(userId);
        when(row.getAuctionId()).thenReturn(auctionId);
        when(row.getAmount()).thenReturn(amount);
        return row;
    }
}
