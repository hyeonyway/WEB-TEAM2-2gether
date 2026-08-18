package com.dbidding.notification.dto;

import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> items,
        Long nextCursor,
        boolean hasNext
) {

    public static NotificationListResponse from(NotificationPage page) {
        return new NotificationListResponse(
                page.items().stream().map(NotificationResponse::from).toList(),
                page.nextCursor(),
                page.hasNext()
        );
    }
}
