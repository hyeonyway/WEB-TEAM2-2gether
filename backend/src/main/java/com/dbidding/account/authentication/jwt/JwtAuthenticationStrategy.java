package com.dbidding.account.authentication.jwt;

import java.time.Clock;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import com.dbidding.account.authentication.AuthenticatedAccount;
import com.dbidding.account.authentication.AuthenticationStrategy;
import com.dbidding.account.dto.LoginResponse;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtAuthenticationStrategy implements AuthenticationStrategy {

	private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

	private final AuthenticationRepository authenticationRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenHasher refreshTokenHasher;
	private final RefreshCookieFactory refreshCookieFactory;
	private final Clock clock;

	@Override
	@Transactional
	public ResponseEntity<?> establish(AuthenticatedAccount account, HttpServletRequest request) {
		IssuedTokens tokens = jwtTokenProvider.issue(account.userId(), account.role(), clock.instant());
		String refreshTokenHash = refreshTokenHasher.hash(tokens.refreshToken());
		authenticationRepository.upsertRefreshTokenHash(account.userId(), refreshTokenHash);
		ResponseCookie refreshCookie = refreshCookieFactory.create(tokens.refreshToken());

		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
			.body(new LoginResponse(tokens.accessToken()));
	}

	@Override
	@Transactional
	public ResponseEntity<Void> terminate(HttpServletRequest request) {
		String refreshToken = findRefreshToken(request);
		if (refreshToken != null && !refreshToken.isBlank()) {
			authenticationRepository.deleteByRefreshTokenHash(refreshTokenHasher.hash(refreshToken));
		}
		ResponseCookie expiredRefreshCookie = refreshCookieFactory.expire();
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, expiredRefreshCookie.toString())
			.build();
	}

	private String findRefreshToken(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}
}
