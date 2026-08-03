package com.dbidding.account.controller;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbidding.account.authentication.AuthenticatedAccount;
import com.dbidding.account.authentication.AuthenticationStrategy;
import com.dbidding.account.authentication.CredentialAuthenticationService;
import com.dbidding.account.cookie.RefreshCookieFactory;
import com.dbidding.account.dto.LoginRequest;
import com.dbidding.account.dto.SignupRequest;
import com.dbidding.account.dto.SignupResponse;
import com.dbidding.account.exception.DuplicateEmailException;
import com.dbidding.account.exception.DuplicateNicknameException;
import com.dbidding.account.exception.InvalidCredentialsException;
import com.dbidding.account.exception.InvalidTokenException;
import com.dbidding.account.service.AuthService;
import com.dbidding.account.service.RefreshResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final RefreshCookieFactory refreshCookieFactory;
	private final CredentialAuthenticationService credentialAuthenticationService;
	private final AuthenticationStrategy authenticationStrategy;

	@PostMapping("/signup")
	public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(
		@Valid @RequestBody LoginRequest request,
		HttpServletRequest httpServletRequest
	) {
		AuthenticatedAccount account = credentialAuthenticationService.authenticate(
			request.email(),
			request.password()
		);
		return authenticationStrategy.establish(account, httpServletRequest);
	}

	@PostMapping("/refresh")
	public ResponseEntity<?> refresh(
		@CookieValue(name = "refreshToken", required = false) String refreshToken
	) {
		if (refreshToken == null || refreshToken.isBlank()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("code", "REFRESH_TOKEN_MISSING"));
		}

		RefreshResult result = authService.refresh(refreshToken);
		ResponseCookie refreshCookie = refreshCookieFactory.create(result.refreshToken());

		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
			.body(result.response());
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(HttpServletRequest request) {
		return authenticationStrategy.terminate(request);
	}

	@ExceptionHandler({
		DuplicateEmailException.class,
		DuplicateNicknameException.class
	})
	public ResponseEntity<Void> handleSignupConflict() {
		return ResponseEntity.status(HttpStatus.CONFLICT).build();
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<Void> handleInvalidCredentials() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}

	@ExceptionHandler(InvalidTokenException.class)
	public ResponseEntity<Void> handleInvalidToken() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}
}
