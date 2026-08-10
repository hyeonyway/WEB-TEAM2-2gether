package com.dbidding.notification.sse;

import com.dbidding.notification.dto.NotificationResponse;

/** Redis 채널로 나가는 wire 메시지 — 어느 유저에게 보낼지까지 함께 담는다. */
public record NotificationPushMessage(Integer userId, NotificationResponse payload) {
}
