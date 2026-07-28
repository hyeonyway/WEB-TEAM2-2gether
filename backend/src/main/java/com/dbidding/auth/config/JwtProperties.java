package com.dbidding.auth.config;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret,
    long accessTokenSeconds,
    long refreshTokenSeconds,
    boolean secureCookie
) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        if (accessTokenSeconds <= 0) {
            throw new IllegalArgumentException("JWT access token expiration must be positive");
        }
        if (refreshTokenSeconds <= 0) {
            throw new IllegalArgumentException("JWT refresh token expiration must be positive");
        }
    }
}
