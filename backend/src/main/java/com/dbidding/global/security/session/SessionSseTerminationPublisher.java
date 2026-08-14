package com.dbidding.global.security.session;

public interface SessionSseTerminationPublisher {

	String CHANNEL = "session:sse:termination";

	void terminate(String sessionId);
}
