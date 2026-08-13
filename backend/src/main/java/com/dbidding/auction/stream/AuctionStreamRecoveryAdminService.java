package com.dbidding.auction.stream;

import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.auction.domain.AuctionBidEventInbox;
import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import com.dbidding.auction.repository.AuctionBidEventInboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("redis")
@RequiredArgsConstructor
public class AuctionStreamRecoveryAdminService {
    private final AccountRepository accountRepository;
    private final AuctionBidEventInboxRepository inboxRepository;
    private final AuctionTimelineStreamPauseRegistry pauseRegistry;

    public AuctionStreamRecoveryStatus status(Integer userId) {
        requireAdmin(userId);
        AuctionBidEventInbox first = inboxRepository
                .findFirstByProjectionStatusInOrderByIdAsc(java.util.List.of(AuctionBidEventProjectionStatus.PENDING, AuctionBidEventProjectionStatus.ERROR))
                .orElse(null);
        return new AuctionStreamRecoveryStatus(
                pauseRegistry.isPaused(),
                inboxRepository.countByProjectionStatus(AuctionBidEventProjectionStatus.PENDING),
                inboxRepository.countByProjectionStatus(AuctionBidEventProjectionStatus.ERROR),
                first == null ? null : first.getStreamId(),
                first == null ? null : first.getFailureMessage()
        );
    }

    private void requireAdmin(Integer userId) {
        boolean admin = accountRepository.findById(userId)
                .map(account -> account.getRole() == AccountRole.ADMIN)
                .orElse(false);
        if (!admin) throw new StreamRecoveryAccessDeniedException();
    }
}
