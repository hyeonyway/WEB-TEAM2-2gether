package com.dbidding.account.authentication;

import org.springframework.stereotype.Service;

import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountStatus;
import com.dbidding.account.exception.InvalidCredentialsException;
import com.dbidding.account.password.PasswordHasher;
import com.dbidding.account.repository.AccountRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CredentialAuthenticationService {

	private static final String DUMMY_PASSWORD_HASH = "0".repeat(64);
	private static final String DUMMY_PASSWORD_SALT = "0".repeat(32);

	private final AccountRepository accountRepository;
	private final PasswordHasher passwordHasher;

	public AuthenticatedAccount authenticate(String email, String rawPassword) {
		Account account = accountRepository.findByEmail(email).orElse(null);
		if (account == null) {
			passwordHasher.matches(rawPassword, DUMMY_PASSWORD_SALT, DUMMY_PASSWORD_HASH);
			throw new InvalidCredentialsException();
		}

		boolean passwordMatches = passwordHasher.matches(
			rawPassword,
			account.getSalt(),
			account.getEncryptedPassword()
		);
		if (!passwordMatches || account.getStatus() != AccountStatus.ACTIVE) {
			throw new InvalidCredentialsException();
		}

		return new AuthenticatedAccount(account.getId(), account.getRole());
	}
}
