package com.dbidding.account.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AuthenticationModePropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void 설정이_없으면_JWT를_기본값으로_사용한다() {
		contextRunner.run(context -> assertThat(context)
			.hasNotFailed()
			.getBean(AuthenticationModeProperties.class)
			.extracting(AuthenticationModeProperties::mode)
			.isEqualTo(AuthenticationMode.JWT));
	}

	@Test
	void session_모드는_명시적으로_허용한_개발_환경에서만_사용할_수_있다() {
		contextRunner.withPropertyValues(
			"app.auth.mode=session",
			"app.auth.session-enabled=true"
		)
			.run(context -> assertThat(context)
				.hasNotFailed()
				.getBean(AuthenticationModeProperties.class)
				.extracting(AuthenticationModeProperties::mode)
				.isEqualTo(AuthenticationMode.SESSION));
	}

	@Test
	void session_모드는_명시적_허용_없이_시작할_수_없다() {
		contextRunner.withPropertyValues("app.auth.mode=session")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure())
					.hasRootCauseMessage("Session authentication requires app.auth.session-enabled=true");
			});
	}

	@Test
	void 알_수_없는_인증_모드는_애플리케이션_시작에_실패한다() {
		contextRunner.withPropertyValues("app.auth.mode=unknown")
			.run(context -> assertThat(context).hasFailed());
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(AuthenticationModeProperties.class)
	static class TestConfiguration {
	}
}
