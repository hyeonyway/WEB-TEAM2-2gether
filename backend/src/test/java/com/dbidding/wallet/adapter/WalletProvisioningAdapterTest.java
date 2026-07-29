package com.dbidding.wallet.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.exception.WalletAlreadyExistsException;
import com.dbidding.wallet.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
class WalletProvisioningAdapterTest {

	@Mock
	private WalletRepository walletRepository;

	@InjectMocks
	private WalletProvisioningAdapter walletProvisioningAdapter;

	@Test
	void 사용자_ID로_잔액_0원_지갑을_생성한다() {
		walletProvisioningAdapter.createFor(1);

		then(walletRepository).should().saveAndFlush(argThat(wallet ->
			wallet.getUserId().equals(1) && wallet.getPoint() == 0L
		));
	}

	@Test
	void 이미_지갑이_있는_사용자에게_지갑을_중복_생성하지_않는다() {
		given(walletRepository.existsByUserId(1)).willReturn(true);

		assertThatThrownBy(() -> walletProvisioningAdapter.createFor(1))
			.isInstanceOf(WalletAlreadyExistsException.class);
		then(walletRepository).should(never()).saveAndFlush(any(Wallet.class));
	}

	@Test
	void 동시_생성으로_사용자_ID_UNIQUE_제약이_충돌하면_중복_지갑_예외로_변환한다() {
		DataIntegrityViolationException duplicateUserId =
			dataIntegrityViolation("wallets.uk_wallets_user_id");
		given(walletRepository.saveAndFlush(any(Wallet.class))).willThrow(duplicateUserId);

		assertThatThrownBy(() -> walletProvisioningAdapter.createFor(1))
			.isInstanceOf(WalletAlreadyExistsException.class)
			.hasCause(duplicateUserId);
	}

	@Test
	void 사용자_ID_UNIQUE가_아닌_무결성_예외는_그대로_전파한다() {
		DataIntegrityViolationException unrelatedConstraint =
			dataIntegrityViolation("fk_wallets_user");
		given(walletRepository.saveAndFlush(any(Wallet.class))).willThrow(unrelatedConstraint);

		Throwable thrown = catchThrowable(() -> walletProvisioningAdapter.createFor(1));

		assertThat(thrown).isSameAs(unrelatedConstraint);
	}

	private DataIntegrityViolationException dataIntegrityViolation(String constraintName) {
		ConstraintViolationException constraintViolation = new ConstraintViolationException(
			"constraint violation",
			new SQLException("constraint violation"),
			constraintName
		);
		return new DataIntegrityViolationException("data integrity violation", constraintViolation);
	}
}
