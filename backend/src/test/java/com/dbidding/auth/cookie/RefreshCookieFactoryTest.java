package com.dbidding.auth.cookie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import com.dbidding.auth.config.JwtProperties;

class RefreshCookieFactoryTest {

	private static final String SECRET = "0123456789abcdef0123456789abcdef";

	@Test
	void 운영_refresh_cookie는_auth_경로의_host_only_보안_쿠키로_생성한다() {
		RefreshCookieFactory factory = new RefreshCookieFactory(
			new JwtProperties(SECRET, 1800, 604800, true)
		);

		ResponseCookie cookie = factory.create("refresh-token");

		assertThat(cookie.getName()).isEqualTo("refreshToken");
		assertThat(cookie.getValue()).isEqualTo("refresh-token");
		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.isSecure()).isTrue();
		assertThat(cookie.getSameSite()).isEqualTo("Strict");
		assertThat(cookie.getPath()).isEqualTo("/api/auth");
		assertThat(cookie.getDomain()).isNull();
		assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofSeconds(604800));
	}

	@Test
	void 로컬에서는_설정에_따라_secure를_비활성화한다() {
		RefreshCookieFactory factory = new RefreshCookieFactory(
			new JwtProperties(SECRET, 1800, 604800, false)
		);

		ResponseCookie cookie = factory.create("refresh-token");

		assertThat(cookie.isSecure()).isFalse();
	}
}
