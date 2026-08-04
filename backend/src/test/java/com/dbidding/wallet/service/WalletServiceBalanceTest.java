package com.dbidding.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.spy;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.exception.InvalidWalletBalanceException;
import com.dbidding.wallet.exception.WalletNotFoundException;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;
import com.dbidding.wallet.metrics.WalletMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class WalletServiceBalanceTest {

	@Mock
	private WalletRepository walletRepository;

	@Mock
	private PointRecordRepository pointRecordRepository;

	@Mock
	private WalletHoldRepository walletHoldRepository;

	private WalletService service;

	@BeforeEach
	void setUp() {
		service = new WalletService(
			walletRepository,
			pointRecordRepository,
			walletHoldRepository,
			new WalletMetrics(new SimpleMeterRegistry())
		);
	}

	@Test
	void 총잔액에서_활성_hold를_빼서_가용액을_계산한다() {
		Wallet wallet = walletWithPoint(100_000L);
		given(walletRepository.findByUserId(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(10)).willReturn(30_000L);

		WalletBalanceResponse result = service.getBalance(1);

		assertThat(result.totalBalance()).isEqualTo(100_000L);
		assertThat(result.frozenBalance()).isEqualTo(30_000L);
		assertThat(result.availableBalance()).isEqualTo(70_000L);
	}

	@Test
	void 활성_hold가_없으면_총잔액_전체를_사용할_수_있다() {
		Wallet wallet = walletWithPoint(100_000L);
		given(walletRepository.findByUserId(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(10)).willReturn(0L);

		WalletBalanceResponse result = service.getBalance(1);

		assertThat(result.frozenBalance()).isZero();
		assertThat(result.availableBalance()).isEqualTo(100_000L);
	}

	@Test
	void wallet이_없으면_404_도메인_예외다() {
		given(walletRepository.findByUserId(1)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.getBalance(1))
			.isInstanceOf(WalletNotFoundException.class);
	}

	@Test
	void 동결액이_총잔액보다_크면_손상된_잔액으로_거절한다() {
		Wallet wallet = walletWithPoint(10_000L);
		given(walletRepository.findByUserId(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(10)).willReturn(10_001L);

		assertThatThrownBy(() -> service.getBalance(1))
			.isInstanceOf(InvalidWalletBalanceException.class);
	}

	private Wallet walletWithPoint(long point) {
		Wallet wallet = spy(Wallet.open(1));
		given(wallet.getId()).willReturn(10);
		wallet.credit(point);
		return wallet;
	}
}
