package com.dbidding.auction.service;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.dto.AuctionCloseResponse;
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.repository.AuctionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AuctionDueClosingService {
    private static final int MAX_CLOSE_ATTEMPTS = 3;

    private final AuctionRepository auctionRepository;
    private final AuctionCommandService auctionCommandService;
    private final Executor auctionCloseTaskExecutor;

    public AuctionDueClosingService(
            AuctionRepository auctionRepository,
            AuctionCommandService auctionCommandService,
            @Qualifier("auctionCloseTaskExecutor") Executor auctionCloseTaskExecutor
    ) {
        this.auctionRepository = auctionRepository;
        this.auctionCommandService = auctionCommandService;
        this.auctionCloseTaskExecutor = auctionCloseTaskExecutor;
    }

    public List<AuctionCloseResponse> closeDueAuctions(Instant now, int limit) {
        if (limit < 1) {
			throw AuctionException.invalidRequest("종료 처리 개수는 1 이상이어야 합니다.");
        }

        List<Integer> auctionIds = auctionRepository.findDueAuctionIds(
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING), now, PageRequest.of(0, limit)
        );
        if (auctionIds.isEmpty()) {
            log.debug("event=auction.close.batch.empty now={} limit={}", now, limit);
            return List.of();
        }

        log.info("event=auction.close.batch.started count={} now={} limit={}", auctionIds.size(), now, limit);
        List<AuctionCloseResponse> responses = auctionIds.stream()
                .map(auctionId -> CompletableFuture.supplyAsync(
                        () -> closeDueAuctionWithRetry(auctionId, now), auctionCloseTaskExecutor
                ))
                .map(CompletableFuture::join)
                .flatMap(Optional::stream)
                .toList();
        log.info("event=auction.close.batch.completed count={} auctionIds={}", responses.size(),
                responses.stream().map(AuctionCloseResponse::auctionId).toList());
        return responses;
    }

    private Optional<AuctionCloseResponse> closeDueAuctionWithRetry(Integer auctionId, Instant now) {
        for (int attempt = 1; attempt <= MAX_CLOSE_ATTEMPTS; attempt++) {
            try {
                return auctionCommandService.closeDueAuction(auctionId, now);
            } catch (PessimisticLockingFailureException exception) {
                if (attempt == MAX_CLOSE_ATTEMPTS) {
                    log.error("event=auction.close.retry_exhausted auctionId={} attempts={}", auctionId, attempt, exception);
                    return Optional.empty();
                }
                log.warn("event=auction.close.retrying auctionId={} attempt={}", auctionId, attempt, exception);
            } catch (RuntimeException exception) {
                log.error("event=auction.close.failed auctionId={}", auctionId, exception);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
