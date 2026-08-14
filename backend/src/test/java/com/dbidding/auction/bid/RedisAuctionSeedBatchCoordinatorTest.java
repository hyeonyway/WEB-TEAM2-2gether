package com.dbidding.auction.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RedisAuctionSeedBatchCoordinatorTest {
    private final AuctionRepository auctionRepository = mock(AuctionRepository.class);
    private final BidRepository bidRepository = mock(BidRepository.class);
    private final AuctionImageRepository auctionImageRepository = mock(AuctionImageRepository.class);
    private final RedisCardStateReader cardStateReader = mock(RedisCardStateReader.class);

    private Auction auction(Integer id, Integer itemId) {
        Auction auction = Auction.builder()
                .sellerId(1).itemId(itemId).auctionName("경매" + id).description("설명")
                .startPrice(10_000L).deliveryFee(0L).openTime(Instant.parse("2026-08-13T00:00:00Z"))
                .estimatedCloseTime(Instant.parse("2026-08-14T00:00:00Z"))
                .closeTime(Instant.parse("2026-08-14T00:00:00Z")).bidPriceUnit(1_000L).build();
        ReflectionTestUtils.setField(auction, "id", id);
        return auction;
    }

    private void stubEmptyBidsAndImages() {
        when(bidRepository.findByAuctionIdInAndStatus(anyList(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(bidRepository.findLatestBidPerBidderByAuctionIdIn(anyList())).thenReturn(List.of());
        when(bidRepository.findRecentFiveByAuctionIdIn(anyList())).thenReturn(List.of());
        when(auctionImageRepository.findByAuctionIdInOrderById(anyList())).thenReturn(List.of());
    }

    @Test
    void 서로_다른_경매가_동시에_콜드미스_나도_실제_조회는_한_번만_수행된다() throws Exception {
        int auctionCount = 20;
        List<Integer> auctionIds = new ArrayList<>();
        List<Auction> auctions = new ArrayList<>();
        for (int i = 1; i <= auctionCount; i++) {
            auctionIds.add(3000000 + i);
            auctions.add(auction(3000000 + i, i));
        }
        stubEmptyBidsAndImages();
        when(auctionRepository.findByIdInAndStatusNot(anyList(), org.mockito.ArgumentMatchers.any())).thenReturn(auctions);
        when(cardStateReader.getCardSnapshots(anyList())).thenReturn(Map.of());
        RedisAuctionSeedBatchCoordinator coordinator = new RedisAuctionSeedBatchCoordinator(
                auctionRepository, bidRepository, auctionImageRepository, cardStateReader, 50, 200
        );

        ExecutorService pool = Executors.newFixedThreadPool(auctionCount);
        CountDownLatch ready = new CountDownLatch(auctionCount);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Optional<AuctionSeedData>>> futures = new ArrayList<>();
        for (Integer auctionId : auctionIds) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return coordinator.requestSeedData(auctionId).join();
            }, pool));
        }
        ready.await();
        start.countDown();
        for (CompletableFuture<Optional<AuctionSeedData>> future : futures) {
            assertThat(future.get(5, TimeUnit.SECONDS)).isPresent();
        }
        pool.shutdown();

        verify(auctionRepository, times(1)).findByIdInAndStatusNot(anyList(), org.mockito.ArgumentMatchers.any());
        verify(bidRepository, times(1)).findByAuctionIdInAndStatus(anyList(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 배치_크기에_도달하면_윈도우를_기다리지_않고_즉시_flush한다() throws Exception {
        int maxBatchSize = 5;
        List<Auction> auctions = new ArrayList<>();
        for (int i = 1; i <= maxBatchSize; i++) auctions.add(auction(3000000 + i, i));
        stubEmptyBidsAndImages();
        when(auctionRepository.findByIdInAndStatusNot(anyList(), org.mockito.ArgumentMatchers.any())).thenReturn(auctions);
        when(cardStateReader.getCardSnapshots(anyList())).thenReturn(Map.of());
        RedisAuctionSeedBatchCoordinator coordinator = new RedisAuctionSeedBatchCoordinator(
                auctionRepository, bidRepository, auctionImageRepository, cardStateReader, 10_000, maxBatchSize
        );

        List<CompletableFuture<Optional<AuctionSeedData>>> futures = new ArrayList<>();
        for (int i = 1; i <= maxBatchSize; i++) {
            futures.add(coordinator.requestSeedData(3000000 + i));
        }
        for (CompletableFuture<Optional<AuctionSeedData>> future : futures) {
            assertThat(future.get(2, TimeUnit.SECONDS)).isPresent();
        }

        verify(auctionRepository, times(1)).findByIdInAndStatusNot(anyList(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 종료된_경매는_빈_값으로_완료된다() {
        stubEmptyBidsAndImages();
        when(auctionRepository.findByIdInAndStatusNot(anyList(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        RedisAuctionSeedBatchCoordinator coordinator = new RedisAuctionSeedBatchCoordinator(
                auctionRepository, bidRepository, auctionImageRepository, cardStateReader, 5, 200
        );

        Optional<AuctionSeedData> result = coordinator.requestSeedData(3000001).join();

        assertThat(result).isEmpty();
    }

    @Test
    void flush_중_예외가_발생하면_대기중이던_모든_호출자가_hang_없이_실패한다() {
        when(auctionRepository.findByIdInAndStatusNot(anyList(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("DB down"));
        RedisAuctionSeedBatchCoordinator coordinator = new RedisAuctionSeedBatchCoordinator(
                auctionRepository, bidRepository, auctionImageRepository, cardStateReader, 5, 200
        );

        CompletableFuture<Optional<AuctionSeedData>> first = coordinator.requestSeedData(3000001);
        CompletableFuture<Optional<AuctionSeedData>> second = coordinator.requestSeedData(3000002);

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

    @SuppressWarnings("unused")
    private CardSnapshot snapshot(Integer itemId) {
        return new CardSnapshot(itemId, "카드", "세트", null, null, "thumbnail");
    }
}
