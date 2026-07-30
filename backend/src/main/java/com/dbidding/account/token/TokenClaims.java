package com.dbidding.account.token;

public record TokenClaims(
    Integer userId,
    TokenType type
) {
}
