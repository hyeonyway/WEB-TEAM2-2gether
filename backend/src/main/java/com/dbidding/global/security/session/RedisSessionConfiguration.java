package com.dbidding.global.security.session;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "session")
@ConditionalOnProperty(name = "app.session.store", havingValue = "redis")
@EnableRedisIndexedHttpSession(redisNamespace = "${SESSION_REDIS_NAMESPACE:dbidding:session}")
public class RedisSessionConfiguration {

	@Bean
	ConfigureRedisAction configureRedisSessionKeyspaceEvents() {
		return ConfigureRedisAction.NO_OP;
	}
}
