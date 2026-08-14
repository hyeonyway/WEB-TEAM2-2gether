package com.dbidding.global.security.session;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@Profile("local-sse")
@RequiredArgsConstructor
public class LocalSessionSseTerminationPublisher implements SessionSseTerminationPublisher {

	private final SessionSseConnectionRegistry registry;

	@Override
	public void terminate(String sessionId) {
		registry.disconnect(sessionId);
	}
}
