package com.dbidding.auth.token;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.dbidding.auth.config.JwtProperties;
import com.dbidding.auth.exception.ExpiredTokenException;
import com.dbidding.auth.exception.InvalidTokenException;
import com.dbidding.auth.exception.InvalidTokenRoleException;
import com.dbidding.auth.exception.InvalidTokenTypeException;
import com.dbidding.auth.port.UserAccountRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedTokens issue(Integer userId, UserAccountRole role, Instant now) {
        Instant accessExpiresAt = now.plusSeconds(properties.accessTokenSeconds());
        Instant refreshExpiresAt = now.plusSeconds(properties.refreshTokenSeconds());

        String accessToken = Jwts.builder()
            .subject(userId.toString())
            .claim("role", role.name())
            .claim("type", TokenType.ACCESS.claimValue())
            .issuedAt(Date.from(now))
            .expiration(Date.from(accessExpiresAt))
            .signWith(secretKey)
            .compact();

        String refreshToken = Jwts.builder()
            .subject(userId.toString())
            .claim("type", TokenType.REFRESH.claimValue())
            .issuedAt(Date.from(now))
            .expiration(Date.from(refreshExpiresAt))
            .signWith(secretKey)
            .compact();

        return new IssuedTokens(accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
    }

    public TokenClaims parseAccess(String token) {
        Claims claims = parseSignedClaims(token);
        validateType(claims, TokenType.ACCESS);
        validateAccessRole(claims);
        return new TokenClaims(parseUserId(claims), TokenType.ACCESS);
    }

    public TokenClaims parseRefresh(String token) {
        Claims claims = parseSignedClaims(token);
        validateType(claims, TokenType.REFRESH);
        return new TokenClaims(parseUserId(claims), TokenType.REFRESH);
    }

    private Claims parseSignedClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            if (claims.getExpiration() == null) {
                throw new InvalidTokenException();
            }
            return claims;
        } catch (ExpiredJwtException exception) {
            throw new ExpiredTokenException(exception);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException(exception);
        }
    }

    private void validateType(Claims claims, TokenType expectedType) {
        Object actualType = claims.get("type");
        if (!(actualType instanceof String type) || !expectedType.claimValue().equals(type)) {
            throw new InvalidTokenTypeException();
        }
    }

    private void validateAccessRole(Claims claims) {
        Object roleClaim = claims.get("role");
        if (!(roleClaim instanceof String role)) {
            throw new InvalidTokenRoleException();
        }
        try {
            UserAccountRole.valueOf(role);
        } catch (IllegalArgumentException exception) {
            throw new InvalidTokenRoleException();
        }
    }

    private Integer parseUserId(Claims claims) {
        try {
            return Integer.valueOf(claims.getSubject());
        } catch (NumberFormatException | NullPointerException exception) {
            throw new InvalidTokenException(exception);
        }
    }
}
