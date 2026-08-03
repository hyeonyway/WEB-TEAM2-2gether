package com.dbidding.account.authentication.jwt;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbidding.account.exception.InvalidTokenException;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class JwtRefreshController {

	private final JwtRefreshService jwtRefreshService;
	private final RefreshCookieFactory refreshCookieFactory;

	@PostMapping("/refresh")
	public ResponseEntity<?> refresh(
		@CookieValue(name = "refreshToken", required = false) String refreshToken
	) {
		if (refreshToken == null || refreshToken.isBlank()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("code", "REFRESH_TOKEN_MISSING"));
		}

		JwtRefreshResult result = jwtRefreshService.refresh(refreshToken);
		ResponseCookie refreshCookie = refreshCookieFactory.create(result.refreshToken());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
			.body(result.response());
	}

	@ExceptionHandler(InvalidTokenException.class)
	public ResponseEntity<Void> handleInvalidToken() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}
}
