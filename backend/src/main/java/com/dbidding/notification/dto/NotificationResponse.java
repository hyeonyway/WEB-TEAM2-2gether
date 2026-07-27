package com.dbidding.notification.dto;

import com.dbidding.notification.Notification;

public record NotificationResponse(
        Long id,
        Integer auctionId,
        String message
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getAuctionId(), notification.getMessage());
    }
}
