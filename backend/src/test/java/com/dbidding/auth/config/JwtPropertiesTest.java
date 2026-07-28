package com.dbidding.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    private static final String VALID_SECRET = "local-development-secret-at-least-32-bytes";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(JwtPropertiesTestConfiguration.class);

    @Test
    void 환경변수에_대응하는_JWT_설정을_바인딩한다() {
        contextRunner
            .withPropertyValues(
                "app.jwt.secret=" + VALID_SECRET,
                "app.jwt.access-token-seconds=1800",
                "app.jwt.refresh-token-seconds=604800",
                "app.jwt.secure-cookie=true"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                JwtProperties properties = context.getBean(JwtProperties.class);
                assertThat(properties.secret()).isEqualTo(VALID_SECRET);
                assertThat(properties.accessTokenSeconds()).isEqualTo(1800);
                assertThat(properties.refreshTokenSeconds()).isEqualTo(604800);
                assertThat(properties.secureCookie()).isTrue();
            });
    }

    @Test
    void HS256_비밀키가_32바이트_미만이면_설정_바인딩에_실패한다() {
        contextRunner
            .withPropertyValues(
                "app.jwt.secret=short-secret",
                "app.jwt.access-token-seconds=1800",
                "app.jwt.refresh-token-seconds=604800",
                "app.jwt.secure-cookie=false"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage("JWT secret must be at least 32 bytes");
            });
    }

    @Test
    void Access_Token_만료시간이_0_이하면_설정_바인딩에_실패한다() {
        contextRunner
            .withPropertyValues(
                "app.jwt.secret=" + VALID_SECRET,
                "app.jwt.access-token-seconds=0",
                "app.jwt.refresh-token-seconds=604800",
                "app.jwt.secure-cookie=false"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage("JWT access token expiration must be positive");
            });
    }

    @Test
    void Refresh_Token_만료시간이_0_이하면_설정_바인딩에_실패한다() {
        contextRunner
            .withPropertyValues(
                "app.jwt.secret=" + VALID_SECRET,
                "app.jwt.access-token-seconds=1800",
                "app.jwt.refresh-token-seconds=0",
                "app.jwt.secure-cookie=false"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage("JWT refresh token expiration must be positive");
            });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtPropertiesTestConfiguration {
    }
}
