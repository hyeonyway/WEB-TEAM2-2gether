package com.dbidding.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dbidding.global.security.CurrentUser;
import com.dbidding.notification.domain.NotificationType;
import com.dbidding.notification.dto.NotificationListResponse;
import com.dbidding.notification.dto.NotificationPage;
import com.dbidding.notification.dto.NotificationUnreadCountResponse;
import com.dbidding.notification.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationListResponse findAll(
            @CurrentUser Integer userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(name = "read", required = false) Boolean read
    ) {
        NotificationPage page = notificationService.findPage(userId, cursor, size, Boolean.FALSE.equals(read));
        return NotificationListResponse.from(page);
    }

    @GetMapping("/unread-count")
    public NotificationUnreadCountResponse unreadCount(@CurrentUser Integer userId) {
        return new NotificationUnreadCountResponse(notificationService.countUnread(userId));
    }

    @PatchMapping("/read")
    public ResponseEntity<Void> markAsRead(
            @CurrentUser Integer userId,
            @RequestParam NotificationType type,
            @RequestParam Integer auctionId,
            @RequestParam(defaultValue = "0") Long bidId
    ) {
        notificationService.markAsRead(userId, type, auctionId, bidId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@CurrentUser Integer userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }
}
