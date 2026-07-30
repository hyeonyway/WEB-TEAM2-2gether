package com.dbidding.notification;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dbidding.global.security.CurrentUser;
import com.dbidding.notification.dto.NotificationResponse;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> findAll(
            @CurrentUser Integer userId,
            @RequestParam(name = "read", required = false) Boolean read
    ) {
        List<Notification> notifications = Boolean.FALSE.equals(read)
                ? notificationService.findUnread(userId)
                : notificationService.findAll(userId);
        return notifications.stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @PatchMapping("/{notificationId}/read")
    public void markAsRead(@CurrentUser Integer userId, @PathVariable Long notificationId) {
        notificationService.markAsRead(userId, notificationId);
    }

    @PatchMapping("/read-all")
    public void markAllAsRead(@CurrentUser Integer userId) {
        notificationService.markAllAsRead(userId);
    }
}
