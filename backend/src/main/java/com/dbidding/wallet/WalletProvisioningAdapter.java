package com.dbidding.wallet;

import org.springframework.stereotype.Component;

import com.dbidding.auth.port.WalletProvisioningPort;
import com.dbidding.wallet.exception.WalletAlreadyExistsException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WalletProvisioningAdapter implements WalletProvisioningPort {

	private final WalletRepository walletRepository;

	@Override
	public void createFor(Integer userId) {
		if (walletRepository.existsByUserId(userId)) {
			throw new WalletAlreadyExistsException();
		}
		walletRepository.save(Wallet.open(userId));
	}
}
