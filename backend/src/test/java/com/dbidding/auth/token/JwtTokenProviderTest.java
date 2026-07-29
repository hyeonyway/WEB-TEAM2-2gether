package com.dbidding.auth.token;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import com.dbidding.auth.config.JwtProperties;
import com.dbidding.auth.exception.ExpiredTokenException;
import com.dbidding.auth.exception.InvalidTokenException;
import com.dbidding.auth.exception.InvalidTokenRoleException;
import com.dbidding.auth.exception.InvalidTokenTypeException;
import com.dbidding.auth.port.UserAccountRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    private final JwtTokenProvider provider = new JwtTokenProvider(
        new JwtProperties(SECRET, 1800, 604800, false)
    );

    @Test
    void Access와_Refresh에_각각_필요한_클레임과_만료시간을_넣는다() {
        IssuedTokens tokens = provider.issue(42, UserAccountRole.USER, NOW);

        Claims accessClaims = parse(tokens.accessToken());
        assertThat(accessClaims.getSubject()).isEqualTo("42");
        assertThat(accessClaims.get("role", String.class)).isEqualTo("USER");
        assertThat(accessClaims.get("type", String.class)).isEqualTo("access");
        assertThat(accessClaims.getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(accessClaims.getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(1800));

        Claims refreshClaims = parse(tokens.refreshToken());
        assertThat(refreshClaims.getSubject()).isEqualTo("42");
        assertThat(refreshClaims).doesNotContainKey("role");
        assertThat(refreshClaims.get("type", String.class)).isEqualTo("refresh");
        assertThat(refreshClaims.getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(refreshClaims.getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(604800));

        assertThat(tokens.accessExpiresAt()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(tokens.refreshExpiresAt()).isEqualTo(NOW.plusSeconds(604800));
    }

    @Test
    void Access_Token을_검증해_사용자_ID와_타입을_반환한다() {
        IssuedTokens tokens = provider.issue(42, UserAccountRole.USER, Instant.now());

        TokenClaims claims = provider.parseAccess(tokens.accessToken());

        assertThat(claims.userId()).isEqualTo(42);
        assertThat(claims.type()).isEqualTo(TokenType.ACCESS);
    }

    @Test
    void Refresh_Token을_검증해_사용자_ID와_타입을_반환한다() {
        IssuedTokens tokens = provider.issue(42, UserAccountRole.USER, Instant.now());

        TokenClaims claims = provider.parseRefresh(tokens.refreshToken());

        assertThat(claims.userId()).isEqualTo(42);
        assertThat(claims.type()).isEqualTo(TokenType.REFRESH);
    }

    @Test
    void 다른_비밀키로_서명한_토큰은_거절한다() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(
            new JwtProperties("fedcba9876543210fedcba9876543210", 1800, 604800, false)
        );
        String token = otherProvider.issue(42, UserAccountRole.USER, Instant.now()).accessToken();

        assertThatThrownBy(() -> provider.parseAccess(token))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void 만료된_토큰은_만료_예외로_변환한다() {
        String token = provider.issue(
            42,
            UserAccountRole.USER,
            Instant.now().minusSeconds(604801)
        ).accessToken();

        assertThatThrownBy(() -> provider.parseAccess(token))
            .isInstanceOf(ExpiredTokenException.class);
    }

    @Test
    void Refresh_Token을_Access_Token으로_사용하면_거절한다() {
        String refreshToken = provider.issue(
            42,
            UserAccountRole.USER,
            Instant.now()
        ).refreshToken();

        assertThatThrownBy(() -> provider.parseAccess(refreshToken))
            .isInstanceOf(InvalidTokenTypeException.class);
    }

    @Test
    void Access_Token을_Refresh_Token으로_사용하면_거절한다() {
        String accessToken = provider.issue(
            42,
            UserAccountRole.USER,
            Instant.now()
        ).accessToken();

        assertThatThrownBy(() -> provider.parseRefresh(accessToken))
            .isInstanceOf(InvalidTokenTypeException.class);
    }

    @Test
    void Access_Token의_role이_허용된_역할이_아니면_거절한다() {
        String token = signedToken("42", "SUPER_ADMIN", "access");

        assertThatThrownBy(() -> provider.parseAccess(token))
            .isInstanceOf(InvalidTokenRoleException.class);
    }

    @Test
    void type_클레임이_문자열이_아니면_토큰_타입_예외로_변환한다() {
        String token = signedToken("42", "USER", 1);

        assertThatThrownBy(() -> provider.parseAccess(token))
            .isInstanceOf(InvalidTokenTypeException.class);
    }

    @Test
    void role_클레임이_문자열이_아니면_토큰_역할_예외로_변환한다() {
        String token = signedToken("42", 1, "access");

        assertThatThrownBy(() -> provider.parseAccess(token))
            .isInstanceOf(InvalidTokenRoleException.class);
    }

    @Test
    void 만료시간이_없는_Access_Token은_거절한다() {
        String token = signedTokenWithoutExpiration("USER", "access");

        assertThatThrownBy(() -> provider.parseAccess(token))
            .isExactlyInstanceOf(InvalidTokenException.class);
    }

    @Test
    void 만료시간이_없는_Refresh_Token은_거절한다() {
        String token = signedTokenWithoutExpiration(null, "refresh");

        assertThatThrownBy(() -> provider.parseRefresh(token))
            .isExactlyInstanceOf(InvalidTokenException.class);
    }

    private String signedToken(String subject, Object role, Object type) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(subject)
            .claim("role", role)
            .claim("type", type)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(1800)))
            .signWith(key)
            .compact();
    }

    private String signedTokenWithoutExpiration(Object role, Object type) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var builder = Jwts.builder()
            .subject("42")
            .claim("type", type)
            .issuedAt(Date.from(Instant.now()));
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.signWith(key).compact();
    }

    private Claims parse(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
            .verifyWith(key)
            .clock(() -> Date.from(NOW))
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
