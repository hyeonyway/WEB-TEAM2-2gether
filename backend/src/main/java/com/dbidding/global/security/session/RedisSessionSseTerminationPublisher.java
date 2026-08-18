package com.dbidding.global.security.session;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisSessionSseTerminationPublisher implements SessionSseTerminationPublisher {

	private final StringRedisTemplate redisTemplate;

	@Override
	public void terminate(String sessionId) {
		redisTemplate.convertAndSend(CHANNEL, sessionId);
	}
}
