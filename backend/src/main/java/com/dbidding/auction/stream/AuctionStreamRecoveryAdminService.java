package com.dbidding.auction.stream;

import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.auction.domain.AuctionBidEventInbox;
import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import com.dbidding.auction.repository.AuctionBidEventInboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;

@Service
@Profile("redis")
@RequiredArgsConstructor
public class AuctionStreamRecoveryAdminService {
    private final AccountRepository accountRepository;
    private final AuctionBidEventInboxRepository inboxRepository;

    public AuctionStreamRecoveryStatus status(Integer userId) {
        requireAdmin(userId);
        AuctionBidEventInbox first = inboxRepository
                .findFirstByProjectionStatusInOrderByIdAsc(java.util.List.of(AuctionBidEventProjectionStatus.PENDING, AuctionBidEventProjectionStatus.ERROR))
                .orElse(null);
        AuctionBidEventInbox latestProcessed = inboxRepository
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
                PageRequest.of(Math.max(0, page), 50)
        );
        return new AuctionStreamRecoveryEventPage(
                result.getContent().stream().map(AuctionStreamRecoveryEventResponse::from).toList(),
                result.getNumber(), result.getTotalPages(), result.getTotalElements()
        );
    }

    private void requireAdmin(Integer userId) {
        boolean admin = accountRepository.findById(userId)
                .map(account -> account.getRole() == AccountRole.ADMIN)
                .orElse(false);
        if (!admin) throw new StreamRecoveryAccessDeniedException();
    }
}
