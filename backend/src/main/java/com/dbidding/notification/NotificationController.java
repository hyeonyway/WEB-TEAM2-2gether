package com.dbidding.notification;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbidding.notification.dto.NotificationResponse;

// TODO: 인증 미들웨어(JwtAuthFilter) 도입되면 @PathVariable Integer userId를 @CurrentUser Integer userId로 교체
@RestController
@RequestMapping("/api/users/{userId}/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> findAll(@PathVariable Integer userId) {
        return notificationService.findAll(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }
}
