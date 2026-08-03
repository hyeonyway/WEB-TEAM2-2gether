package com.dbidding.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dbidding.notification.dto.NotificationResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationSseConnectionManagerTest {

    @Test
    void 연결한_emitter에_알림_생성_이벤트를_전송한다() throws Exception {
        NotificationSseConnectionManager manager = new NotificationSseConnectionManager();
        SseEmitter emitter = mock(SseEmitter.class);
        manager.register(1, emitter);

        manager.push(1, notification());

        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(manager.connectionCount(1)).isEqualTo(1);
    }

    @Test
    void 다른_유저의_연결에는_전송하지_않는다() throws Exception {
        NotificationSseConnectionManager manager = new NotificationSseConnectionManager();
        SseEmitter emitter = mock(SseEmitter.class);
        manager.register(1, emitter);

        manager.push(2, notification());

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 전송에_실패한_emitter는_연결_목록에서_제거한다() throws Exception {
        NotificationSseConnectionManager manager = new NotificationSseConnectionManager();
        SseEmitter emitter = mock(SseEmitter.class);
        manager.register(1, emitter);
        doThrow(new IOException("disconnected"))
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        manager.push(1, notification());

        assertThat(manager.connectionCount(1)).isZero();
        verify(emitter).complete();
    }

    @Test
    void 접속중인_연결이_없으면_아무일도_하지_않는다() {
        NotificationSseConnectionManager manager = new NotificationSseConnectionManager();

        manager.push(1, notification());

        assertThat(manager.connectionCount(1)).isZero();
    }

    private NotificationResponse notification() {
        return new NotificationResponse(1L, 100, "메시지", false, LocalDateTime.of(2026, 7, 30, 12, 0));
    }
}
