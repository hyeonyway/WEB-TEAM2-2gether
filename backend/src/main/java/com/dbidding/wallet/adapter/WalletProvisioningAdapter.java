package com.dbidding.wallet.adapter;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.dbidding.auth.port.WalletProvisioningPort;
import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.exception.WalletAlreadyExistsException;
import com.dbidding.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WalletProvisioningAdapter implements WalletProvisioningPort {

	private static final String USER_ID_UNIQUE_CONSTRAINT = "uk_wallets_user_id";

	private final WalletRepository walletRepository;

	@Override
	public void createFor(Integer userId) {
		if (walletRepository.existsByUserId(userId)) {
			throw new WalletAlreadyExistsException();
		}
		try {
			walletRepository.saveAndFlush(Wallet.open(userId));
		} catch (DataIntegrityViolationException exception) {
			if (isUserIdUniqueConstraintViolation(exception)) {
				throw new WalletAlreadyExistsException(exception);
			}
			throw exception;
		}
	}

	private boolean isUserIdUniqueConstraintViolation(Throwable exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException constraintViolation) {
				String constraintName = constraintViolation.getConstraintName();
				if (constraintName == null) {
					return false;
				}
				String normalizedName = constraintName.replace("`", "");
				String unqualifiedName = normalizedName.substring(normalizedName.lastIndexOf('.') + 1);
				return unqualifiedName.equalsIgnoreCase(USER_ID_UNIQUE_CONSTRAINT);
			}
			cause = cause.getCause();
		}
		return false;
	}
}
