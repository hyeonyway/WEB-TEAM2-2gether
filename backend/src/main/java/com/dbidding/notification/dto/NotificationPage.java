package com.dbidding.notification.dto;

import com.dbidding.notification.domain.Notification;
import java.util.List;

public record NotificationPage(List<Notification> items, Long nextCursor, boolean hasNext) {
}
