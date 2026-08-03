package com.dbidding.account.authentication.jwt;

public record TokenClaims(
    Integer userId,
    TokenType type
) {
}
