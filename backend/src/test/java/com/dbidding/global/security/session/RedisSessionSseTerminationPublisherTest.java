package com.dbidding.global.security.session;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisSessionSseTerminationPublisherTest {

	@Test
	void 세션_종료를_모든_인스턴스에_전파할_채널로_발행한다() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		RedisSessionSseTerminationPublisher publisher = new RedisSessionSseTerminationPublisher(redisTemplate);

		publisher.terminate("session-a");

		verify(redisTemplate).convertAndSend(SessionSseTerminationPublisher.CHANNEL, "session-a");
	}
}
