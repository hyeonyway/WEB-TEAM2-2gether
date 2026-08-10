package com.dbidding.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, jdbcTemplate);
    }

    @Test
    void 알림을_저장한다() {
        given(notificationRepository.save(any(Notification.class))).willAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.save(1, 10, NotificationType.AUCTION_OPENED, "찜한 카드의 경매가 등록되었습니다.");

        assertThat(result.getUserId()).isEqualTo(1);
        assertThat(result.getAuctionId()).isEqualTo(10);
        assertThat(result.getMessage()).isEqualTo("찜한 카드의 경매가 등록되었습니다.");
        assertThat(result.getBidId()).isEqualTo(Notification.NO_BID);
    }

    @Test
    void bid에_연결된_알림을_저장한다() {
        given(notificationRepository.save(any(Notification.class))).willAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.saveForBid(1, 10, NotificationType.OUTBID, 42L, "상회 입찰이 발생했습니다.");

        assertThat(result.getUserId()).isEqualTo(1);
        assertThat(result.getAuctionId()).isEqualTo(10);
        assertThat(result.getBidId()).isEqualTo(42L);
    }

    @Test
    void 여러_유저의_알림을_한번에_저장하고_존재하는_행까지_함께_조회한다() {
        List<Integer> userIds = List.of(1, 2, 3);
        List<Notification> existing = List.of(
                Notification.of(1, 10, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."),
                Notification.of(2, 10, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."),
                Notification.of(3, 10, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다.")
        );
        given(notificationRepository.findByAuctionIdAndTypeAndBidIdAndUserIdIn(
                10, NotificationType.AUCTION_OPENED, Notification.NO_BID, userIds
        )).willReturn(existing);

        List<Notification> result = notificationService.saveAllIgnoringDuplicates(
                userIds, 10, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."
        );

        then(jdbcTemplate).should().update(
                eq("INSERT IGNORE INTO notification (user_id, auction_id, type, bid_id, message) VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?), (?, ?, ?, ?, ?)"),
                eq(1), eq(10), eq("AUCTION_OPENED"), eq(Notification.NO_BID), eq("리자몽 EX 카드의 경매가 등록되었습니다."),
                eq(2), eq(10), eq("AUCTION_OPENED"), eq(Notification.NO_BID), eq("리자몽 EX 카드의 경매가 등록되었습니다."),
                eq(3), eq(10), eq("AUCTION_OPENED"), eq(Notification.NO_BID), eq("리자몽 EX 카드의 경매가 등록되었습니다.")
        );
        assertThat(result).isEqualTo(existing);
    }

    @Test
    void 대상_유저가_없으면_저장_없이_빈_목록을_반환한다() {
        List<Notification> result = notificationService.saveAllIgnoringDuplicates(
                List.of(), 10, NotificationType.AUCTION_OPENED, "메시지"
        );

        assertThat(result).isEmpty();
        then(jdbcTemplate).shouldHaveNoInteractions();
        then(notificationRepository).should(never()).findByAuctionIdAndTypeAndBidIdAndUserIdIn(any(), any(), any(), any());
    }

    @Test
    void 알림_목록을_첫_페이지로_조회한다() {
        given(notificationRepository.findByUserIdOrderByIdDesc(1, PageRequest.of(0, 21)))
                .willReturn(List.of(Notification.of(1, 10, NotificationType.AUCTION_OPENED, "메시지")));

        NotificationPage result = notificationService.findPage(1, null, 20, false);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getAuctionId()).isEqualTo(10);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 안읽은_알림_목록을_조회한다() {
        given(notificationRepository.findByUserIdAndIsReadFalseOrderByIdDesc(1, PageRequest.of(0, 21)))
                .willReturn(List.of(Notification.of(1, 10, NotificationType.AUCTION_OPENED, "메시지")));

        NotificationPage result = notificationService.findPage(1, null, 20, true);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).isRead()).isFalse();
    }

    @Test
    void cursor가_있으면_해당_id_미만의_알림을_조회한다() {
        given(notificationRepository.findByUserIdAndIdLessThanOrderByIdDesc(1, 42L, PageRequest.of(0, 21)))
                .willReturn(List.of(Notification.of(1, 10, NotificationType.AUCTION_OPENED, "메시지")));

        NotificationPage result = notificationService.findPage(1, 42L, 20, false);

        assertThat(result.items()).hasSize(1);
    }

    @Test
    void cursor와_안읽음_필터를_함께_적용한다() {
        given(notificationRepository.findByUserIdAndIsReadFalseAndIdLessThanOrderByIdDesc(1, 42L, PageRequest.of(0, 21)))
                .willReturn(List.of(Notification.of(1, 10, NotificationType.AUCTION_OPENED, "메시지")));

        NotificationPage result = notificationService.findPage(1, 42L, 20, true);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).isRead()).isFalse();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void size가_유효_범위를_벗어나면_400을_던진다() {
        assertThatThrownBy(() -> notificationService.findPage(1, null, 0, false))
                .isInstanceOf(NotificationException.class);
        assertThatThrownBy(() -> notificationService.findPage(1, null, -1, false))
                .isInstanceOf(NotificationException.class);
        assertThatThrownBy(() -> notificationService.findPage(1, null, 101, false))
                .isInstanceOf(NotificationException.class);
    }

    @Test
    void 다음_페이지가_있으면_size만큼만_반환하고_nextCursor를_채운다() {
        List<Notification> fetched = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) {
            fetched.add(Notification.of(1, i, NotificationType.AUCTION_OPENED, "메시지" + i));
        }
        ReflectionTestUtils.setField(fetched.get(19), "id", 99L);
        given(notificationRepository.findByUserIdOrderByIdDesc(1, PageRequest.of(0, 21))).willReturn(fetched);

        NotificationPage result = notificationService.findPage(1, null, 20, false);

        assertThat(result.items()).hasSize(20);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(99L);
    }

    @Test
    void 안읽음_개수를_조회한다() {
        given(notificationRepository.countByUserIdAndIsReadFalse(1)).willReturn(3L);

        long result = notificationService.countUnread(1);

        assertThat(result).isEqualTo(3L);
    }

    @Test
    void 본인_알림을_읽음_처리한다() {
        Notification notification = Notification.of(1, 10, NotificationType.AUCTION_OPENED, "메시지");
        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));

        notificationService.markAsRead(1, 1L);

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void 존재하지_않는_알림을_읽음_처리하면_404() {
        given(notificationRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(1, 1L))
                .isInstanceOf(NotificationException.class);
    }

    @Test
    void 본인_소유가_아닌_알림을_읽음_처리하면_404() {
        Notification notification = Notification.of(2, 10, NotificationType.AUCTION_OPENED, "메시지");
        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(1, 1L))
                .isInstanceOf(NotificationException.class);
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    void 전체_알림을_읽음_처리한다() {
        notificationService.markAllAsRead(1);

        then(notificationRepository).should().markAllAsReadByUserId(1);
    }
}
