package com.dbidding.auction.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.account.repository.AccountRepository;
import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import com.dbidding.auction.repository.AuctionTimelineEventRepository;
import org.junit.jupiter.api.Test;

class AuctionStreamRecoveryAdminServiceTest {
    @Test
    void 관리자가_재처리할_오류가_없으면_대기건수와_안내문을_반환한다() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuctionTimelineEventRepository inbox = mock(AuctionTimelineEventRepository.class);
        AuctionBidStreamPersistenceService persistence = mock(AuctionBidStreamPersistenceService.class);
        admin(accounts, 1);
        when(persistence.requeueFirstError()).thenReturn(null);
        when(inbox.countByProjectionStatus(AuctionBidEventProjectionStatus.PENDING)).thenReturn(3L);

        AuctionStreamRecoveryReplayResponse response = new AuctionStreamRecoveryAdminService(accounts, inbox, persistence).replay(1);

        assertThat(response.accepted()).isFalse();
        assertThat(response.pendingCount()).isEqualTo(3L);
        assertThat(response.message()).contains("없습니다");
    }

    @Test
    void 관리자가_아니면_복구_정보를_조회할_수_없다() {
        AccountRepository accounts = mock(AccountRepository.class);
        when(accounts.findById(2)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> new AuctionStreamRecoveryAdminService(accounts, mock(AuctionTimelineEventRepository.class),
                mock(AuctionBidStreamPersistenceService.class)).status(2))
                .isInstanceOf(StreamRecoveryAccessDeniedException.class);
    }

    private void admin(AccountRepository accounts, int userId) {
        com.dbidding.account.domain.Account account = mock(com.dbidding.account.domain.Account.class);
        when(account.getRole()).thenReturn(com.dbidding.account.domain.AccountRole.ADMIN);
        when(accounts.findById(userId)).thenReturn(java.util.Optional.of(account));
    }
}
