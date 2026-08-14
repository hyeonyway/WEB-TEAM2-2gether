package com.dbidding.global.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.session.data.redis.config.ConfigureNotifyKeyspaceEventsAction;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

class RedisSessionConfigurationTest {

	@Test
	void Redis_저장소를_선택하면_사용자별_세션_조회가_가능한_인덱스_세션을_선언한다() {
		assertThat(RedisSessionConfiguration.class)
			.hasAnnotation(EnableRedisIndexedHttpSession.class);
	}

	@Test
	void Redis_세션의_유휴_만료를_감지하도록_keyspace_이벤트를_활성화한다() {
		var configuration = new RedisSessionConfiguration();

		assertThat(configuration.configureRedisSessionKeyspaceEvents())
			.isInstanceOf(ConfigureNotifyKeyspaceEventsAction.class);
	}
}
