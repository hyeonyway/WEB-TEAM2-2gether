package com.dbidding.auth;

public record TokenClaims(
    Integer userId,
    TokenType type
) {
}
