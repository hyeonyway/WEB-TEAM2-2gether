package com.dbidding.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
    }

    @Test
    void 알림을_저장한다() {
        given(notificationRepository.save(any(Notification.class))).willAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.save(1, 10, "찜한 카드의 경매가 등록되었습니다.");

        assertThat(result.getUserId()).isEqualTo(1);
        assertThat(result.getAuctionId()).isEqualTo(10);
        assertThat(result.getMessage()).isEqualTo("찜한 카드의 경매가 등록되었습니다.");
    }

    @Test
    void 알림_목록을_조회한다() {
        given(notificationRepository.findByUserIdOrderByIdDesc(1))
                .willReturn(List.of(Notification.of(1, 10, "메시지")));

        List<Notification> result = notificationService.findAll(1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAuctionId()).isEqualTo(10);
    }

    @Test
    void 안읽은_알림_목록을_조회한다() {
        given(notificationRepository.findByUserIdAndIsReadFalseOrderByIdDesc(1))
                .willReturn(List.of(Notification.of(1, 10, "메시지")));

        List<Notification> result = notificationService.findUnread(1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isRead()).isFalse();
    }

    @Test
    void 본인_알림을_읽음_처리한다() {
        Notification notification = Notification.of(1, 10, "메시지");
        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));

        notificationService.markAsRead(1, 1L);

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void 존재하지_않는_알림을_읽음_처리하면_404() {
        given(notificationRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(1, 1L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 본인_소유가_아닌_알림을_읽음_처리하면_404() {
        Notification notification = Notification.of(2, 10, "메시지");
        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(1, 1L))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    void 전체_알림을_읽음_처리한다() {
        notificationService.markAllAsRead(1);

        then(notificationRepository).should().markAllAsReadByUserId(1);
    }
}
