package com.dbidding.account.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dbidding.account.domain.Account;
import com.dbidding.account.dto.SignupRequest;
import com.dbidding.account.dto.SignupResponse;
import com.dbidding.account.exception.DuplicateEmailException;
import com.dbidding.account.exception.DuplicateNicknameException;
import com.dbidding.account.password.PasswordHash;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthTransactionService {

	private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";
	private static final String NICKNAME_UNIQUE_CONSTRAINT = "uk_users_nickname";

	private final AccountRepository accountRepository;
	private final WalletService walletService;

	@Transactional
	public SignupResponse createAccountWithWallet(SignupRequest request, PasswordHash password) {
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
		walletService.provision(account.getId());
		return SignupResponse.from(account);
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
