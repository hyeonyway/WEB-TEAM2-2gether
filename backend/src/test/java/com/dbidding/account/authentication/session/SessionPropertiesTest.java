package com.dbidding.account.authentication.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SessionPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void 설정이_없으면_인메모리_저장소와_개발용_쿠키_기본값을_사용한다() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(SessionProperties.class))
				.extracting(
					SessionProperties::store,
					SessionProperties::cookieName,
					SessionProperties::secureCookie
				)
				.containsExactly(SessionStore.MEMORY, "SESSION", false);
		});
	}

	@Test
	void 이번_단계에서_지원하지_않는_저장소는_애플리케이션_시작에_실패한다() {
		contextRunner.withPropertyValues("app.session.store=redis")
			.run(context -> assertThat(context).hasFailed());
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(SessionProperties.class)
	static class TestConfiguration {
	}
}
