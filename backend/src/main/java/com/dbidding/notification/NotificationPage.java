package com.dbidding.notification;

import java.util.List;

public record NotificationPage(List<Notification> items, Long nextCursor, boolean hasNext) {
}
