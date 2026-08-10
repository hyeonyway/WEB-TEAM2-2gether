package com.dbidding.notification.sse;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.dbidding.global.security.CurrentUserProvider;

@WebMvcTest(NotificationSseController.class)
class NotificationSseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationSseConnectionManager connectionManager;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @Test
    void 경로의_유저와_인증된_유저가_같으면_스트림을_연결한다() throws Exception {
        given(currentUserProvider.getCurrentUserId()).willReturn(1);
        given(connectionManager.connect(1)).willReturn(new SseEmitter());

        mockMvc.perform(get("/api/users/1/notifications/stream"))
                .andExpect(request().asyncStarted());

        verify(connectionManager).connect(1);
    }

    @Test
    void 경로의_유저와_인증된_유저가_다르면_401을_반환한다() throws Exception {
        given(currentUserProvider.getCurrentUserId()).willReturn(2);

        mockMvc.perform(get("/api/users/1/notifications/stream"))
                .andExpect(status().isUnauthorized());
    }
}
