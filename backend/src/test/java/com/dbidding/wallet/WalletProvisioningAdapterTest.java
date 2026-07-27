package com.dbidding.wallet;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dbidding.wallet.exception.WalletAlreadyExistsException;

@ExtendWith(MockitoExtension.class)
class WalletProvisioningAdapterTest {

	@Mock
	private WalletRepository walletRepository;

	@InjectMocks
	private WalletProvisioningAdapter walletProvisioningAdapter;

	@Test
	void 사용자_ID로_잔액_0원_지갑을_생성한다() {
		walletProvisioningAdapter.createFor(1);

		then(walletRepository).should().save(argThat(wallet ->
			wallet.getUserId().equals(1) && wallet.getPoint() == 0L
		));
	}

	@Test
	void 이미_지갑이_있는_사용자에게_지갑을_중복_생성하지_않는다() {
		given(walletRepository.existsByUserId(1)).willReturn(true);

		assertThatThrownBy(() -> walletProvisioningAdapter.createFor(1))
			.isInstanceOf(WalletAlreadyExistsException.class);
		then(walletRepository).should(never()).save(argThat(wallet -> true));
	}
}
