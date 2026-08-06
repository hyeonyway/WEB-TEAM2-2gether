package com.dbidding.account.authentication.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountStatus;
import com.dbidding.account.dto.RefreshResponse;
import com.dbidding.account.exception.InvalidRefreshTokenException;
import com.dbidding.account.repository.AccountRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtRefreshService {

	private final AccountRepository accountRepository;
	private final AuthenticationRepository authenticationRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenHasher refreshTokenHasher;

	@Transactional
	public JwtRefreshResult refresh(String refreshToken) {
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

		return new JwtRefreshResult(
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
