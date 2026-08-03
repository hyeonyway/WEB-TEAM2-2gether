package com.dbidding.account.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dbidding.account.authentication.AuthenticatedAccount;
import com.dbidding.account.authentication.CredentialAuthenticationService;
import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountStatus;
import com.dbidding.account.domain.Authentication;
import com.dbidding.account.dto.LoginRequest;
import com.dbidding.account.dto.LoginResponse;
import com.dbidding.account.dto.RefreshResponse;
import com.dbidding.account.dto.SignupRequest;
import com.dbidding.account.dto.SignupResponse;
import com.dbidding.account.exception.DuplicateEmailException;
import com.dbidding.account.exception.DuplicateNicknameException;
import com.dbidding.account.exception.InvalidRefreshTokenException;
import com.dbidding.account.password.PasswordHash;
import com.dbidding.account.password.PasswordHasher;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.account.repository.AuthenticationRepository;
import com.dbidding.account.token.IssuedTokens;
import com.dbidding.account.token.JwtTokenProvider;
import com.dbidding.account.token.RefreshTokenHasher;
import com.dbidding.account.token.TokenClaims;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final AccountRepository accountRepository;
	private final PasswordHasher passwordHasher;
	private final AuthenticationRepository authenticationRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenHasher refreshTokenHasher;
	private final AuthTransactionService authTransactionService;
	private final CredentialAuthenticationService credentialAuthenticationService;

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
		AuthenticatedAccount account = credentialAuthenticationService.authenticate(
			request.email(),
			request.password()
		);
		IssuedTokens tokens = jwtTokenProvider.issue(account.userId(), account.role(), Instant.now());
		String refreshTokenHash = refreshTokenHasher.hash(tokens.refreshToken());
		authTransactionService.persistRefreshToken(account.userId(), refreshTokenHash);

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
