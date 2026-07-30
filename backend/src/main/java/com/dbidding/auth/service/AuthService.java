package com.dbidding.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountStatus;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.auth.domain.Authentication;
import com.dbidding.auth.dto.LoginRequest;
import com.dbidding.auth.dto.LoginResponse;
import com.dbidding.auth.dto.RefreshResponse;
import com.dbidding.auth.dto.SignupRequest;
import com.dbidding.auth.dto.SignupResponse;
import com.dbidding.auth.exception.DuplicateEmailException;
import com.dbidding.auth.exception.DuplicateNicknameException;
import com.dbidding.auth.exception.InvalidCredentialsException;
import com.dbidding.auth.exception.InvalidRefreshTokenException;
import com.dbidding.auth.password.PasswordHash;
import com.dbidding.auth.password.PasswordHasher;
import com.dbidding.auth.port.WalletProvisioningPort;
import com.dbidding.auth.repository.AuthenticationRepository;
import com.dbidding.auth.token.IssuedTokens;
import com.dbidding.auth.token.JwtTokenProvider;
import com.dbidding.auth.token.RefreshTokenHasher;
import com.dbidding.auth.token.TokenClaims;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private static final String DUMMY_PASSWORD_HASH = "0".repeat(64);
	private static final String DUMMY_PASSWORD_SALT = "0".repeat(32);
	private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";
	private static final String NICKNAME_UNIQUE_CONSTRAINT = "uk_users_nickname";

	private final AccountRepository accountRepository;
	private final WalletProvisioningPort walletProvisioningPort;
	private final PasswordHasher passwordHasher;
	private final AuthenticationRepository authenticationRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenHasher refreshTokenHasher;

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		if (accountRepository.existsByEmail(request.email())) {
			throw new DuplicateEmailException();
		}
		if (accountRepository.existsByNickname(request.nickname())) {
			throw new DuplicateNicknameException();
		}

		PasswordHash password = passwordHasher.hash(request.password());
		Account account = Account.create(
			request.email(),
			request.nickname(),
			password.encryptedPassword(),
			password.salt()
		);
		try {
			account = accountRepository.saveAndFlush(account);
		} catch (DataIntegrityViolationException exception) {
			if (isConstraintViolation(exception, EMAIL_UNIQUE_CONSTRAINT)) {
				throw new DuplicateEmailException(exception);
			}
			if (isConstraintViolation(exception, NICKNAME_UNIQUE_CONSTRAINT)) {
				throw new DuplicateNicknameException(exception);
			}
			throw exception;
		}
		walletProvisioningPort.createFor(account.getId());

		return SignupResponse.from(account);
	}

	@Transactional
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
		authenticationRepository.upsertRefreshTokenHash(account.getId(), refreshTokenHash);

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

	private boolean isConstraintViolation(Throwable exception, String expectedConstraint) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException constraintViolation) {
				String constraintName = constraintViolation.getConstraintName();
				if (constraintName == null) {
					return false;
				}
				String normalizedName = constraintName.replace("`", "");
				String unqualifiedName = normalizedName.substring(normalizedName.lastIndexOf('.') + 1);
				return unqualifiedName.equalsIgnoreCase(expectedConstraint);
			}
			cause = cause.getCause();
		}
		return false;
	}
}
