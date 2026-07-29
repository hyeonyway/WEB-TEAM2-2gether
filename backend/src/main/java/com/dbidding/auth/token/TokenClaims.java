package com.dbidding.auth.token;

public record TokenClaims(
    Integer userId,
    TokenType type
) {
}
