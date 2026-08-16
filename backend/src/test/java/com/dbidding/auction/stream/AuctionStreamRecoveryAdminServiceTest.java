package com.dbidding.auction.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.account.repository.AccountRepository;
import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import com.dbidding.auction.repository.AuctionTimelineEventRepository;
import com.dbidding.auction.domain.AuctionTimelineEvent;
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

    @Test
    void 관리자는_미완료와_최근_처리_이벤트를_포함한_복구_상태를_조회한다() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuctionTimelineEventRepository inbox = mock(AuctionTimelineEventRepository.class);
        admin(accounts, 1);
        AuctionTimelineEvent pending = mock(AuctionTimelineEvent.class);
        AuctionTimelineEvent processed = mock(AuctionTimelineEvent.class);
        when(pending.getStreamId()).thenReturn("pending-1");
        when(pending.getFailureMessage()).thenReturn("projection failed");
        when(processed.getStreamId()).thenReturn("processed-1");
        java.time.Instant processedAt = java.time.Instant.parse("2026-08-10T12:00:00Z");
        when(processed.getProcessedAt()).thenReturn(processedAt);
        when(inbox.findFirstByProjectionStatusInOrderByIdAsc(org.mockito.ArgumentMatchers.anyList())).thenReturn(java.util.Optional.of(pending));
        when(inbox.findFirstByProjectionStatusOrderByProcessedAtDesc(AuctionBidEventProjectionStatus.PROCESSED)).thenReturn(java.util.Optional.of(processed));
        when(inbox.countByProjectionStatus(AuctionBidEventProjectionStatus.PENDING)).thenReturn(2L);
        when(inbox.countByProjectionStatus(AuctionBidEventProjectionStatus.ERROR)).thenReturn(1L);
        when(inbox.countByProjectionStatus(AuctionBidEventProjectionStatus.PROCESSED)).thenReturn(5L);

        AuctionStreamRecoveryStatus status = new AuctionStreamRecoveryAdminService(accounts, inbox,
                mock(AuctionBidStreamPersistenceService.class)).status(1);

        assertThat(status.pendingCount()).isEqualTo(2L);
        assertThat(status.errorCount()).isEqualTo(1L);
        assertThat(status.firstIncompleteStreamId()).isEqualTo("pending-1");
        assertThat(status.latestProcessedAt()).isEqualTo(processedAt);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 이벤트_목록은_음수_페이지를_0으로_보정하고_응답으로_변환한다() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuctionTimelineEventRepository inbox = mock(AuctionTimelineEventRepository.class);
        admin(accounts, 1);
        AuctionTimelineEvent event = mock(AuctionTimelineEvent.class);
        when(event.getStreamId()).thenReturn("error-1");
        when(event.getProjectionStatus()).thenReturn(AuctionBidEventProjectionStatus.ERROR);
        org.springframework.data.domain.Page<AuctionTimelineEvent> page = mock(org.springframework.data.domain.Page.class);
        when(page.getContent()).thenReturn(java.util.List.of(event));
        when(page.getNumber()).thenReturn(0);
        when(page.getTotalPages()).thenReturn(1);
        when(page.getTotalElements()).thenReturn(1L);
        when(inbox.findByProjectionStatusInOrderByIdAsc(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(page);

        AuctionStreamRecoveryEventPage result = new AuctionStreamRecoveryAdminService(accounts, inbox,
                mock(AuctionBidStreamPersistenceService.class)).events(1, -1);

        assertThat(result.page()).isZero();
        assertThat(result.content()).extracting(AuctionStreamRecoveryEventResponse::streamId).containsExactly("error-1");
    }

    @Test
    void 관리자는_첫_오류를_재처리_대기열로_되돌릴_수_있다() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuctionTimelineEventRepository inbox = mock(AuctionTimelineEventRepository.class);
        AuctionBidStreamPersistenceService persistence = mock(AuctionBidStreamPersistenceService.class);
        admin(accounts, 1);
        AuctionTimelineEvent requeued = mock(AuctionTimelineEvent.class);
        when(requeued.getStreamId()).thenReturn("error-1");
        when(persistence.requeueFirstError()).thenReturn(requeued);
        when(inbox.countByProjectionStatus(AuctionBidEventProjectionStatus.PENDING)).thenReturn(4L);

        AuctionStreamRecoveryReplayResponse result = new AuctionStreamRecoveryAdminService(accounts, inbox, persistence).replay(1);

        assertThat(result.accepted()).isTrue();
        assertThat(result.replayFromStreamId()).isEqualTo("error-1");
        assertThat(result.pendingCount()).isEqualTo(4L);
    }

    private void admin(AccountRepository accounts, int userId) {
        com.dbidding.account.domain.Account account = mock(com.dbidding.account.domain.Account.class);
        when(account.getRole()).thenReturn(com.dbidding.account.domain.AccountRole.ADMIN);
        when(accounts.findById(userId)).thenReturn(java.util.Optional.of(account));
    }
}
