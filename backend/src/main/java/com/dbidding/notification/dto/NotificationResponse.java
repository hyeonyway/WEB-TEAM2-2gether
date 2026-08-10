package com.dbidding.notification.dto;

import java.time.Instant;

import com.dbidding.notification.Notification;
import com.dbidding.notification.NotificationType;

public record NotificationResponse(
        Long id,
        Integer auctionId,
        NotificationType type,
        String message,
        boolean isRead,
        Instant createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getAuctionId(),
                notification.getType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
