package com.dbidding.notification.dto;

import java.time.Instant;

import com.dbidding.notification.domain.Notification;
import com.dbidding.notification.domain.NotificationType;

public record NotificationResponse(
        Long id,
        Integer auctionId,
        NotificationType type,
        Long bidId,
        String message,
        boolean isRead,
        Instant createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getAuctionId(),
                notification.getType(),
                notification.getBidId(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
