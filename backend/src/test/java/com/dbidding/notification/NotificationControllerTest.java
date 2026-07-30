package com.dbidding.notification;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.dbidding.global.security.CurrentUserProvider;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @BeforeEach
    void setUp() {
        given(currentUserProvider.getCurrentUserId()).willReturn(1);
    }

    @Test
    void 알림_목록을_조회하면_200과_아이템_및_페이지_정보를_반환한다() throws Exception {
        given(notificationService.findPage(1, null, 20, false)).willReturn(new NotificationPage(
                List.of(
                        Notification.of(1, 10, "메시지1"),
                        Notification.of(1, 20, "메시지2")
                ),
                null,
                false
        ));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notifications"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.items.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[1].auctionId").value(20))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[1].message").value("메시지2"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.hasNext").value(false))
                .andExpect(MockMvcResultMatchers.jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void 알림이_없으면_빈_목록을_반환한다() throws Exception {
        given(notificationService.findPage(1, null, 20, false)).willReturn(new NotificationPage(List.of(), null, false));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notifications"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.items.length()").value(0));
    }

    @Test
    void read_false_파라미터면_안읽은_알림만_조회한다() throws Exception {
        given(notificationService.findPage(1, null, 20, true))
                .willReturn(new NotificationPage(List.of(Notification.of(1, 10, "메시지1")), null, false));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notifications").param("read", "false"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.items.length()").value(1));
    }

    @Test
    void cursor와_size_파라미터를_그대로_전달한다() throws Exception {
        given(notificationService.findPage(1, 42L, 5, false))
                .willReturn(new NotificationPage(List.of(Notification.of(1, 10, "메시지1")), null, true));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notifications")
                        .param("cursor", "42")
                        .param("size", "5"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.hasNext").value(true));
    }

    @Test
    void 안읽음_개수를_조회한다() throws Exception {
        given(notificationService.countUnread(1)).willReturn(3L);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notifications/unread-count"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.count").value(3));
    }

    @Test
    void 개별_알림을_읽음_처리한다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/notifications/1/read"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        then(notificationService).should().markAsRead(1, 1L);
    }

    @Test
    void 전체_알림을_읽음_처리한다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/notifications/read-all"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        then(notificationService).should().markAllAsRead(1);
    }
}
