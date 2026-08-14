package com.dbidding.auction.stream;

import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.auction.domain.AuctionTimelineEvent;
import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import com.dbidding.auction.repository.AuctionTimelineEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;

@Service
@Profile("redis")
@RequiredArgsConstructor
public class AuctionStreamRecoveryAdminService {
    private final AccountRepository accountRepository;
    private final AuctionTimelineEventRepository inboxRepository;
    private final AuctionBidStreamPersistenceService persistenceService;

    public AuctionStreamRecoveryStatus status(Integer userId) {
        requireAdmin(userId);
        AuctionTimelineEvent first = inboxRepository
                .findFirstByProjectionStatusInOrderByIdAsc(java.util.List.of(AuctionBidEventProjectionStatus.PENDING, AuctionBidEventProjectionStatus.ERROR))
                .orElse(null);
        AuctionTimelineEvent latestProcessed = inboxRepository
                .findFirstByProjectionStatusOrderByProcessedAtDesc(AuctionBidEventProjectionStatus.PROCESSED)
                .orElse(null);
        return new AuctionStreamRecoveryStatus(
                inboxRepository.countByProjectionStatus(AuctionBidEventProjectionStatus.PENDING),
                inboxRepository.countByProjectionStatus(AuctionBidEventProjectionStatus.ERROR),
                inboxRepository.countByProjectionStatus(AuctionBidEventProjectionStatus.PROCESSED),
                first == null ? null : first.getStreamId(),
                first == null ? null : first.getFailureMessage(),
                latestProcessed == null ? null : latestProcessed.getStreamId(),
                latestProcessed == null ? null : latestProcessed.getProcessedAt()
        );
    }

    public AuctionStreamRecoveryEventPage events(Integer userId, int page) {
        requireAdmin(userId);
        var result = inboxRepository.findByProjectionStatusInOrderByIdAsc(
                java.util.List.of(AuctionBidEventProjectionStatus.PENDING, AuctionBidEventProjectionStatus.ERROR),
                PageRequest.of(Math.max(0, page), 10)
        );
        return new AuctionStreamRecoveryEventPage(
                result.getContent().stream().map(AuctionStreamRecoveryEventResponse::from).toList(),
                result.getNumber(), result.getTotalPages(), result.getTotalElements()
        );
    }

    public AuctionStreamRecoveryEventPage processedEvents(Integer userId, int page) {
        requireAdmin(userId);
        var result = inboxRepository.findByProjectionStatusOrderByProcessedAtDesc(
                AuctionBidEventProjectionStatus.PROCESSED, PageRequest.of(Math.max(0, page), 10)
        );
        return new AuctionStreamRecoveryEventPage(
                result.getContent().stream().map(AuctionStreamRecoveryEventResponse::from).toList(),
                result.getNumber(), result.getTotalPages(), result.getTotalElements()
        );
    }

    public AuctionStreamRecoveryReplayResponse replay(Integer userId) {
        requireAdmin(userId);
        AuctionTimelineEvent requeued = persistenceService.requeueFirstError();
        if (requeued == null) {
            return new AuctionStreamRecoveryReplayResponse(false, null,
                    inboxRepository.countByProjectionStatus(AuctionBidEventProjectionStatus.PENDING),
                    "재처리할 ERROR 이벤트가 없습니다.");
        }
        return new AuctionStreamRecoveryReplayResponse(true, requeued.getStreamId(),
                inboxRepository.countByProjectionStatus(AuctionBidEventProjectionStatus.PENDING),
                "오류 이벤트를 재처리 대기열에 넣었습니다. DB inbox 순서로 projection을 재개합니다.");
    }

    private void requireAdmin(Integer userId) {
        boolean admin = accountRepository.findById(userId)
                .map(account -> account.getRole() == AccountRole.ADMIN)
                .orElse(false);
        if (!admin) throw new StreamRecoveryAccessDeniedException();
    }
}
