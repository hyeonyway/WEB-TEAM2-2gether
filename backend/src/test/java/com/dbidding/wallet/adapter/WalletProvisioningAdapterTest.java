package com.dbidding.wallet.adapter;

import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dbidding.wallet.service.WalletService;

@ExtendWith(MockitoExtension.class)
class WalletProvisioningAdapterTest {

	@Mock
	private WalletService walletService;

	@InjectMocks
	private WalletProvisioningAdapter adapter;

	@Test
	void 지갑_생성을_WalletService에_위임한다() {
		adapter.createFor(1);

		then(walletService).should().provision(1);
	}
}
