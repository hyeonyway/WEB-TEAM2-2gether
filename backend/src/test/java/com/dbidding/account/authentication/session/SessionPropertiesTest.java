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
	void 저장소를_명시하지_않으면_애플리케이션_시작에_실패한다() {
		contextRunner.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void 인메모리_저장소를_명시하면_안전한_쿠키_기본값을_사용한다() {
		contextRunner.withPropertyValues("app.session.store=memory")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(SessionProperties.class))
					.extracting(
						SessionProperties::store,
						SessionProperties::cookieName,
						SessionProperties::secureCookie
					)
					.containsExactly(SessionStore.MEMORY, "SESSION", true);
			});
	}

	@Test
	void 로컬_환경은_secure_cookie를_명시적으로_해제할_수_있다() {
		contextRunner.withPropertyValues(
			"app.session.store=memory",
			"app.session.secure-cookie=false"
		).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(SessionProperties.class).secureCookie()).isFalse();
		});
	}

	@Test
	void SameSite_정책을_환경별로_설정할_수_있다() {
		contextRunner.withPropertyValues(
			"app.session.store=memory",
			"app.session.same-site=none"
		).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(SessionProperties.class).sameSite()).isEqualTo("None");
		});
	}

	@Test
	void SameSite_None은_secure_cookie_false와_함께_설정할_수_없다() {
		contextRunner.withPropertyValues(
			"app.session.store=memory",
			"app.session.same-site=none",
			"app.session.secure-cookie=false"
		).run(context -> assertThat(context).hasFailed());
	}

	@Test
	void Redis_저장소를_명시하면_애플리케이션_시작에_성공한다() {
		contextRunner.withPropertyValues("app.session.store=redis")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(SessionProperties.class).store()).isEqualTo(SessionStore.REDIS);
			});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(SessionProperties.class)
	static class TestConfiguration {
	}
}
