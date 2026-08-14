package com.dbidding.global.security.session;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

class SessionSseTerminationRedisSubscriberTest {

	@Test
	void 세션_종료_메시지를_받으면_현재_인스턴스의_연결을_종료한다() {
		SessionSseConnectionRegistry registry = mock(SessionSseConnectionRegistry.class);
		SessionSseTerminationRedisSubscriber subscriber = new SessionSseTerminationRedisSubscriber(registry);
		Message message = mock(Message.class);
		when(message.getBody()).thenReturn("session-a".getBytes(StandardCharsets.UTF_8));

		subscriber.onMessage(message, null);

		verify(registry).disconnect("session-a");
	}
}
