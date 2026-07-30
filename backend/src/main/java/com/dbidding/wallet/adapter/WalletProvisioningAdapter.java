package com.dbidding.wallet.adapter;

import org.springframework.stereotype.Component;

import com.dbidding.auth.port.WalletProvisioningPort;
import com.dbidding.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WalletProvisioningAdapter implements WalletProvisioningPort {

	private final WalletService walletService;

	@Override
	public void createFor(Integer userId) {
		walletService.provision(userId);
	}
}
