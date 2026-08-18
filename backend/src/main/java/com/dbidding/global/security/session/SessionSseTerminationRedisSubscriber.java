package com.dbidding.global.security.session;

import java.nio.charset.StandardCharsets;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SessionSseTerminationRedisSubscriber implements MessageListener {

	private final SessionSseConnectionRegistry registry;

	@Override
	public void onMessage(Message message, byte[] pattern) {
		registry.disconnect(new String(message.getBody(), StandardCharsets.UTF_8));
	}
}
