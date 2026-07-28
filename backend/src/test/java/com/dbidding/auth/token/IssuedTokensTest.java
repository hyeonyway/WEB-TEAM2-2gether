package com.dbidding.auth.token;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IssuedTokensTest {

    @Test
    void 문자열_표현에_Access와_Refresh_Token_원문을_노출하지_않는다() {
        IssuedTokens tokens = new IssuedTokens(
            "raw-access-token",
            "raw-refresh-token",
            Instant.parse("2026-07-28T00:30:00Z"),
            Instant.parse("2026-08-04T00:00:00Z")
        );

        assertThat(tokens.toString())
            .doesNotContain("raw-access-token")
            .doesNotContain("raw-refresh-token");
    }
}
