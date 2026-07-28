package com.dbidding.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbidding.auth.cookie.RefreshCookieFactory;
import com.dbidding.auth.dto.LoginRequest;
import com.dbidding.auth.dto.LoginResponse;
import com.dbidding.auth.dto.SignupRequest;
import com.dbidding.auth.dto.SignupResponse;
import com.dbidding.auth.exception.DuplicateEmailException;
import com.dbidding.auth.exception.DuplicateNicknameException;
import com.dbidding.auth.exception.InvalidCredentialsException;
import com.dbidding.auth.service.AuthService;
import com.dbidding.auth.service.LoginResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final RefreshCookieFactory refreshCookieFactory;

	@PostMapping("/signup")
	public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
	}

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		LoginResult result = authService.login(request);
		ResponseCookie refreshCookie = refreshCookieFactory.create(result.refreshToken());

		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
			.body(result.response());
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
}
