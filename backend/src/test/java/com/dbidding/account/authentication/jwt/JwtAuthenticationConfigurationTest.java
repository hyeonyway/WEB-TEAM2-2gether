package com.dbidding.account.authentication.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.dbidding.account.authentication.AuthenticationStrategy;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.global.security.RequestUserIdWriter;
import com.dbidding.global.security.jwt.JwtAuthFilter;
import com.dbidding.global.security.jwt.SseTicketAuthFilter;
import com.dbidding.global.security.jwt.SseTicketController;
import com.dbidding.global.security.jwt.TicketProvider;

class JwtAuthenticationConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfiguration.class)
		.withBean(AccountRepository.class, () -> mock(AccountRepository.class))
		.withBean(AuthenticationRepository.class, () -> mock(AuthenticationRepository.class))
		.withBean(RequestUserIdWriter.class, RequestUserIdWriter::new)
		.withBean(Clock.class, Clock::systemUTC)
		.withPropertyValues(
			"app.jwt.secret=0123456789abcdef0123456789abcdef",
			"app.jwt.access-token-seconds=1800",
			"app.jwt.refresh-token-seconds=604800",
			"app.jwt.secure-cookie=true"
		);

	@Test
	void 기본_모드에서_JWT_전략과_필터와_전용_엔드포인트를_등록한다() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(AuthenticationStrategy.class);
			assertThat(context).hasSingleBean(JwtTokenProvider.class);
			assertThat(context).hasSingleBean(JwtAuthFilter.class);
			assertThat(context).hasSingleBean(SseTicketAuthFilter.class);
			assertThat(context).hasSingleBean(TicketProvider.class);
			assertThat(context).hasSingleBean(JwtRefreshController.class);
			assertThat(context).hasSingleBean(SseTicketController.class);
		});
	}

	@Test
	void session_모드에서는_JWT_구성과_전용_엔드포인트를_등록하지_않는다() {
		contextRunner.withPropertyValues("app.auth.mode=session")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(AuthenticationStrategy.class);
				assertThat(context).doesNotHaveBean(JwtAuthFilter.class);
				assertThat(context).doesNotHaveBean(JwtRefreshController.class);
				assertThat(context).doesNotHaveBean(SseTicketController.class);
			});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(JwtProperties.class)
	@Import({
		JwtAuthenticationConfiguration.class,
		JwtRefreshController.class,
		SseTicketController.class
	})
	static class TestConfiguration {
	}
}
