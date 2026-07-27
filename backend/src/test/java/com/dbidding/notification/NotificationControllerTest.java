package com.dbidding.notification;

import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void 알림_목록을_조회하면_200과_목록을_반환한다() throws Exception {
        given(notificationService.findAll(1)).willReturn(List.of(
                Notification.of(1, 10, "메시지1"),
                Notification.of(1, 20, "메시지2")
        ));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/users/1/notifications"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].auctionId").value(20))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].message").value("메시지2"));
    }

    @Test
    void 알림이_없으면_빈_목록을_반환한다() throws Exception {
        given(notificationService.findAll(1)).willReturn(List.of());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/users/1/notifications"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(0));
    }
}
