package com.dbidding.global.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

class RedisSessionConfigurationTest {

	@Test
	void Redis_저장소를_선택하면_사용자별_세션_조회가_가능한_인덱스_세션을_선언한다() {
		assertThat(RedisSessionConfiguration.class)
			.hasAnnotation(EnableRedisIndexedHttpSession.class);
	}
}
