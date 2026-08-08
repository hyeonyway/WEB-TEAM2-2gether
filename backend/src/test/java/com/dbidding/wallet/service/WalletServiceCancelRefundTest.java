package com.dbidding.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.spy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dbidding.wallet.domain.PointRecord;
import com.dbidding.wallet.domain.PointTransactionType;
import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.exception.InvalidWalletAmountException;
import com.dbidding.wallet.exception.WalletNotFoundException;
import com.dbidding.wallet.metrics.WalletMetrics;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class WalletServiceCancelRefundTest {

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
			new WalletMetrics(new SimpleMeterRegistry()),
			Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC)
		);
	}

	@Test
	void 구매_취소_환불은_잠긴_지갑에_양수_원장과_함께_입금된다() {
		Wallet wallet = spy(Wallet.open(1));
		given(wallet.getId()).willReturn(10);
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(pointRecordRepository.save(any(PointRecord.class)))
			.willAnswer(invocation -> invocation.getArgument(0));

		service.cancelRefund(1, 10, 50_000L);

		assertThat(wallet.getPoint()).isEqualTo(50_000L);
		then(pointRecordRepository).should().save(argThat(record ->
			record.getTransactionType() == PointTransactionType.ORDER_CANCEL_REFUND
				&& record.getAuctionId().equals(10)
				&& record.getAmount() == 50_000L
				&& record.getBalance() == 50_000L
		));
	}

	@Test
	void 취소_환불_금액은_양수여야_한다() {
		assertThatThrownBy(() -> service.cancelRefund(1, 10, 0L))
			.isInstanceOf(InvalidWalletAmountException.class);

		then(walletRepository).shouldHaveNoInteractions();
	}

	@Test
	void 구매자_지갑이_없으면_취소_환불을_거절한다() {
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.cancelRefund(1, 10, 50_000L))
			.isInstanceOf(WalletNotFoundException.class);
	}
}
