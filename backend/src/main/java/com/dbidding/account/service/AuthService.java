package com.dbidding.account.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountStatus;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.account.domain.Authentication;
import com.dbidding.account.dto.LoginRequest;
import com.dbidding.account.dto.LoginResponse;
import com.dbidding.account.dto.RefreshResponse;
import com.dbidding.account.dto.SignupRequest;
import com.dbidding.account.dto.SignupResponse;
import com.dbidding.account.exception.DuplicateEmailException;
import com.dbidding.account.exception.DuplicateNicknameException;
import com.dbidding.account.exception.InvalidCredentialsException;
import com.dbidding.account.exception.InvalidRefreshTokenException;
import com.dbidding.account.password.PasswordHash;
import com.dbidding.account.password.PasswordHasher;
import com.dbidding.account.repository.AuthenticationRepository;
import com.dbidding.account.token.IssuedTokens;
import com.dbidding.account.token.JwtTokenProvider;
import com.dbidding.account.token.RefreshTokenHasher;
import com.dbidding.account.token.TokenClaims;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private static final String DUMMY_PASSWORD_HASH = "0".repeat(64);
	private static final String DUMMY_PASSWORD_SALT = "0".repeat(32);
	private final AccountRepository accountRepository;
	private final PasswordHasher passwordHasher;
	private final AuthenticationRepository authenticationRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenHasher refreshTokenHasher;
	private final AuthTransactionService authTransactionService;

	public SignupResponse signup(SignupRequest request) {
		if (accountRepository.existsByEmail(request.email())) {
			throw new DuplicateEmailException();
		}
		if (accountRepository.existsByNickname(request.nickname())) {
			throw new DuplicateNicknameException();
		}

		PasswordHash password = passwordHasher.hash(request.password());
		return authTransactionService.createAccountWithWallet(request, password);
	}

	public LoginResult login(LoginRequest request) {
		Account account = accountRepository.findByEmail(request.email()).orElse(null);
		if (account == null) {
			passwordHasher.matches(
				request.password(),
				DUMMY_PASSWORD_SALT,
				DUMMY_PASSWORD_HASH
			);
			throw new InvalidCredentialsException();
		}

		boolean passwordMatches = passwordHasher.matches(
			request.password(),
			account.getSalt(),
			account.getEncryptedPassword()
		);
		if (!passwordMatches || account.getStatus() != AccountStatus.ACTIVE) {
			throw new InvalidCredentialsException();
		}

		IssuedTokens tokens = jwtTokenProvider.issue(account.getId(), account.getRole(), Instant.now());
		String refreshTokenHash = refreshTokenHasher.hash(tokens.refreshToken());
		authTransactionService.persistRefreshToken(account.getId(), refreshTokenHash);

		return new LoginResult(
			new LoginResponse(tokens.accessToken()),
			tokens.refreshToken()
		);
	}

	@Transactional
	public RefreshResult refresh(String refreshToken) {
		TokenClaims claims = jwtTokenProvider.parseRefresh(refreshToken);
		Authentication authentication = authenticationRepository.findByUserIdForUpdate(claims.userId())
			.orElseThrow(InvalidRefreshTokenException::new);

		String presentedHash = refreshTokenHasher.hash(refreshToken);
		if (!hashesMatch(presentedHash, authentication.getRefreshTokenHash())) {
			throw new InvalidRefreshTokenException();
		}

		Account account = accountRepository.findById(claims.userId())
			.filter(foundAccount -> foundAccount.getStatus() == AccountStatus.ACTIVE)
			.orElseThrow(InvalidRefreshTokenException::new);
		IssuedTokens nextTokens = jwtTokenProvider.issue(account.getId(), account.getRole(), Instant.now());
		authentication.rotate(refreshTokenHasher.hash(nextTokens.refreshToken()));

		return new RefreshResult(
			new RefreshResponse(nextTokens.accessToken()),
			nextTokens.refreshToken()
		);
	}

	@Transactional
	public void logout(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			return;
		}

		String refreshTokenHash = refreshTokenHasher.hash(refreshToken);
		authenticationRepository.deleteByRefreshTokenHash(refreshTokenHash);
	}

	private boolean hashesMatch(String presentedHash, String storedHash) {
		return MessageDigest.isEqual(
			presentedHash.getBytes(StandardCharsets.US_ASCII),
			storedHash.getBytes(StandardCharsets.US_ASCII)
		);
	}

}
