package com.dbidding.global.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.dbidding.account.authentication.AuthenticationStrategy;
import com.dbidding.account.authentication.session.SessionAuthenticationStrategy;
import com.dbidding.global.security.RequestUserIdWriter;
import com.dbidding.global.security.FilterErrorResponseWriter;
import tools.jackson.databind.ObjectMapper;

class SessionAuthConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfiguration.class)
		.withBean(RequestUserIdWriter.class, RequestUserIdWriter::new)
		.withBean(FilterErrorResponseWriter.class, () -> new FilterErrorResponseWriter(new ObjectMapper()))
		.withBean(SessionSseConnectionRegistry.class, SessionSseConnectionRegistry::new)
		.withBean(Clock.class, Clock::systemUTC);

	@Test
	void session_memory_모드에서_세션_전략과_필터를_등록한다() {
		contextRunner.withPropertyValues(
			"app.auth.mode=session",
			"app.session.store=memory"
		).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(AuthenticationStrategy.class);
			assertThat(context).hasSingleBean(SessionAuthenticationStrategy.class);
			assertThat(context).hasSingleBean(SessionAuthFilter.class);
			assertThat(context).hasSingleBean(SessionCsrfFilter.class);
		});
	}

	@Test
	void JWT_모드에서는_세션_구성을_등록하지_않는다() {
		contextRunner.withPropertyValues("app.auth.mode=jwt")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(SessionAuthenticationStrategy.class);
				assertThat(context).doesNotHaveBean(SessionAuthFilter.class);
				assertThat(context).doesNotHaveBean(SessionCsrfFilter.class);
			});
	}

	@Configuration(proxyBeanMethods = false)
	@Import(SessionAuthConfiguration.class)
	static class TestConfiguration {
	}
}
