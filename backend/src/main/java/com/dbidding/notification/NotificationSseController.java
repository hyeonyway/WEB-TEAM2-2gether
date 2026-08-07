package com.dbidding.notification;

import com.dbidding.global.exception.UnauthorizedException;
import com.dbidding.global.security.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "jwt", matchIfMissing = true)
@RequiredArgsConstructor
public class NotificationSseController {
    private final NotificationSseConnectionManager connectionManager;

    @GetMapping(value = "/api/users/{userId}/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable Integer userId,
            @CurrentUser Integer currentUserId,
            HttpServletResponse response
    ) {
        if (!userId.equals(currentUserId)) {
            throw new UnauthorizedException();
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return connectionManager.connect(currentUserId);
    }
}
