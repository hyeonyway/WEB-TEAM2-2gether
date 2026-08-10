package com.dbidding.global.security.session;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SessionSseCleanupListener implements HttpSessionListener {

	private final SessionSseConnectionRegistry registry;

	@Override
	public void sessionDestroyed(HttpSessionEvent event) {
		registry.disconnect(event.getSession().getId());
	}
}
