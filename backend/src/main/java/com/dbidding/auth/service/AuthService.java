package com.dbidding.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.dbidding.auth.port.UserAccount;
import com.dbidding.auth.port.UserAccountPort;
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

	private final UserAccountPort userAccountPort;
	private final WalletProvisioningPort walletProvisioningPort;
	private final PasswordHasher passwordHasher;
	private final AuthenticationRepository authenticationRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenHasher refreshTokenHasher;

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		if (userAccountPort.existsByEmail(request.email())) {
			throw new DuplicateEmailException();
		}
		if (userAccountPort.existsByNickname(request.nickname())) {
			throw new DuplicateNicknameException();
		}

		PasswordHash password = passwordHasher.hash(request.password());
		UserAccount user = userAccountPort.create(
			request.email(),
			request.nickname(),
			password.encryptedPassword(),
			password.salt()
		);
		walletProvisioningPort.createFor(user.id());

		return SignupResponse.from(user);
	}

	@Transactional
	public LoginResult login(LoginRequest request) {
		UserAccount user = userAccountPort.findByEmail(request.email()).orElse(null);
		if (user == null) {
			passwordHasher.matches(
				request.password(),
				DUMMY_PASSWORD_SALT,
				DUMMY_PASSWORD_HASH
			);
			throw new InvalidCredentialsException();
		}

		boolean passwordMatches = passwordHasher.matches(
			request.password(),
			user.salt(),
			user.encryptedPassword()
		);
		if (!passwordMatches || !"ACTIVE".equals(user.status())) {
			throw new InvalidCredentialsException();
		}

		IssuedTokens tokens = jwtTokenProvider.issue(user.id(), user.role(), Instant.now());
		String refreshTokenHash = refreshTokenHasher.hash(tokens.refreshToken());
		authenticationRepository.upsertRefreshTokenHash(user.id(), refreshTokenHash);

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

		UserAccount user = userAccountPort.findById(claims.userId())
			.filter(account -> "ACTIVE".equals(account.status()))
			.orElseThrow(InvalidRefreshTokenException::new);
		IssuedTokens nextTokens = jwtTokenProvider.issue(user.id(), user.role(), Instant.now());
		authentication.rotate(refreshTokenHasher.hash(nextTokens.refreshToken()));

		return new RefreshResult(
			new RefreshResponse(nextTokens.accessToken()),
			nextTokens.refreshToken()
		);
	}

	private boolean hashesMatch(String presentedHash, String storedHash) {
		return MessageDigest.isEqual(
			presentedHash.getBytes(StandardCharsets.US_ASCII),
			storedHash.getBytes(StandardCharsets.US_ASCII)
		);
	}
}
