package com.dbidding.notification.dto;

import java.time.LocalDateTime;

import com.dbidding.notification.Notification;

public record NotificationResponse(
        Long id,
        Integer auctionId,
        String message,
        boolean isRead,
        LocalDateTime createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getAuctionId(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
