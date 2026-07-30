package com.dbidding.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
