package com.dbidding.global.security.session;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.dbidding.global.security.CurrentUser;
import com.dbidding.notification.sse.NotificationSseConnectionManager;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "session")
public class SessionNotificationSseController {
	private final NotificationSseConnectionManager connectionManager;

	@GetMapping(value = "/api/me/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@CurrentUser Integer userId, HttpSession session, HttpServletResponse response) {
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
		response.setHeader("X-Accel-Buffering", "no");
		return connectionManager.connect(userId, session.getId());
	}
}
