package com.dbidding.notification.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class NotificationException extends ApiException {

    private NotificationException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static NotificationException invalidPageSize(String message) {
        return new NotificationException(HttpStatus.BAD_REQUEST, "INVALID_NOTIFICATION_PAGE_SIZE", message);
    }

    public static NotificationException notFound() {
        return new NotificationException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "존재하지 않는 알림입니다.");
    }
}
