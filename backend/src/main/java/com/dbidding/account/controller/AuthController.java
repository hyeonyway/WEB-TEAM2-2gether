package com.dbidding.account.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbidding.account.authentication.AuthenticatedAccount;
import com.dbidding.account.authentication.AuthenticationStrategy;
import com.dbidding.account.authentication.CredentialAuthenticationService;
import com.dbidding.account.dto.LoginRequest;
import com.dbidding.account.dto.SignupRequest;
import com.dbidding.account.dto.SignupResponse;
import com.dbidding.account.exception.DuplicateEmailException;
import com.dbidding.account.exception.DuplicateNicknameException;
import com.dbidding.account.exception.InvalidCredentialsException;
import com.dbidding.account.service.SignupService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final SignupService signupService;
	private final CredentialAuthenticationService credentialAuthenticationService;
	private final AuthenticationStrategy authenticationStrategy;

	@PostMapping("/signup")
	public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(signupService.signup(request));
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

}
