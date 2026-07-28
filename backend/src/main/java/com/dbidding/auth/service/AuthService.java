package com.dbidding.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dbidding.auth.dto.SignupRequest;
import com.dbidding.auth.dto.SignupResponse;
import com.dbidding.auth.exception.DuplicateEmailException;
import com.dbidding.auth.exception.DuplicateNicknameException;
import com.dbidding.auth.password.PasswordHash;
import com.dbidding.auth.password.PasswordHasher;
import com.dbidding.auth.port.UserAccount;
import com.dbidding.auth.port.UserAccountPort;
import com.dbidding.auth.port.WalletProvisioningPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserAccountPort userAccountPort;
	private final WalletProvisioningPort walletProvisioningPort;
	private final PasswordHasher passwordHasher;

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
}
