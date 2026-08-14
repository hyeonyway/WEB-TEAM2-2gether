package com.dbidding.global.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

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
		.withBean(SessionSseTerminationPublisher.class, () -> mock(SessionSseTerminationPublisher.class))
		.withBean(Clock.class, Clock::systemUTC);

	@Test
	void session_memory_모드에서_세션_전략과_필터를_등록한다() {
		contextRunner.withPropertyValues(
			"app.session.store=memory"
		).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(SessionAuthenticationStrategy.class);
			assertThat(context).hasSingleBean(SessionAuthFilter.class);
			assertThat(context).hasSingleBean(SessionCsrfFilter.class);
		});
	}

	@Test
	void 세션_구성은_인증_모드_설정과_무관하게_등록한다() {
		contextRunner.withPropertyValues("app.session.store=memory")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(SessionAuthenticationStrategy.class);
				assertThat(context).hasSingleBean(SessionAuthFilter.class);
				assertThat(context).hasSingleBean(SessionCsrfFilter.class);
			});
	}

	@Configuration(proxyBeanMethods = false)
	@Import(SessionAuthConfiguration.class)
	static class TestConfiguration {
	}
}
