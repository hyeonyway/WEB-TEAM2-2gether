package com.dbidding.notification.dto;

import com.dbidding.notification.domain.Notification;
import com.dbidding.notification.domain.NotificationType;

public record NotificationInsertRow(Integer userId, Integer auctionId, NotificationType type, Long bidId, String message) {

    public static NotificationInsertRow of(Integer userId, Integer auctionId, NotificationType type, String message) {
        return new NotificationInsertRow(userId, auctionId, type, Notification.NO_BID, message);
    }
}
