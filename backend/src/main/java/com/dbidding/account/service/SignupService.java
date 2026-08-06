package com.dbidding.account.service;

import org.springframework.stereotype.Service;

import com.dbidding.account.repository.AccountRepository;
import com.dbidding.account.dto.SignupRequest;
import com.dbidding.account.dto.SignupResponse;
import com.dbidding.account.exception.DuplicateEmailException;
import com.dbidding.account.exception.DuplicateNicknameException;
import com.dbidding.account.password.PasswordHash;
import com.dbidding.account.password.PasswordHasher;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SignupService {

	private final AccountRepository accountRepository;
	private final PasswordHasher passwordHasher;
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
}
