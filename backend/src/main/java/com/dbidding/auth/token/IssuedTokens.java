package com.dbidding.auth.token;

import java.time.Instant;

public record IssuedTokens(
    String accessToken,
    String refreshToken,
    Instant accessExpiresAt,
    Instant refreshExpiresAt
) {

    @Override
    public String toString() {
        return "IssuedTokens[accessToken=<redacted>, refreshToken=<redacted>, accessExpiresAt="
            + accessExpiresAt + ", refreshExpiresAt=" + refreshExpiresAt + "]";
    }
}
