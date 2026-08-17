package com.dbidding.global.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SessionMeSseControllerTest {

    @Test
    void 세션ID로_스트림을_연결하고_캐시_비활성화_헤더를_설정한다() {
        MeSseConnectionManager connectionManager = mock(MeSseConnectionManager.class);
        SseEmitter emitter = mock(SseEmitter.class);
        when(connectionManager.connect(1, "session-a")).thenReturn(emitter);
        SessionMeSseController controller = new SessionMeSseController(connectionManager);
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn("session-a");
        HttpServletResponse response = mock(HttpServletResponse.class);

        SseEmitter result = controller.stream(1, session, response);

        assertThat(result).isSameAs(emitter);
        verify(response).setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        verify(response).setHeader("X-Accel-Buffering", "no");
    }
}
