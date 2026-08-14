package com.dbidding.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.dbidding.wallet.domain.PointRecord;
import com.dbidding.wallet.domain.PointTransactionType;
import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.dto.WalletTransactionResponse;
import com.dbidding.wallet.exception.IdempotencyConflictException;
import com.dbidding.wallet.exception.InsufficientAvailableBalanceException;
import com.dbidding.wallet.exception.InvalidIdempotencyKeyException;
import com.dbidding.wallet.exception.InvalidWalletAmountException;
import com.dbidding.wallet.exception.WalletNotFoundException;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;
import com.dbidding.wallet.metrics.WalletMetrics;
import com.dbidding.wallet.sse.WalletBalanceChangedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class WalletServiceTransactionTest {

	@Mock
	private WalletRepository walletRepository;

	@Mock
	private PointRecordRepository pointRecordRepository;

	@Mock
	private WalletHoldRepository walletHoldRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private WalletService service;

	@BeforeEach
	void setUp() {
		service = new WalletService(
			walletRepository,
			pointRecordRepository,
			walletHoldRepository,
			new WalletMetrics(new SimpleMeterRegistry()),
			Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC),
			eventPublisher
		);
	}

	@Test
	void 충전하면_잠긴_wallet과_CHARGE_원장을_같이_변경한다() {
		Wallet wallet = walletWithPoint(0L);
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(pointRecordRepository.save(any(PointRecord.class)))
			.willAnswer(invocation -> invocation.getArgument(0));

		WalletTransactionResponse response = service.charge(1, 10_000L, "charge-key");

		assertThat(wallet.getPoint()).isEqualTo(10_000L);
		assertThat(response.amount()).isEqualTo(10_000L);
		assertThat(response.balance()).isEqualTo(10_000L);
		then(pointRecordRepository).should().save(argThat(record ->
			record.getTransactionType() == PointTransactionType.CHARGE
				&& record.getAmount() == 10_000L
				&& record.getBalance() == 10_000L
		));
	}

	@Test
	void 직접_충전은_영속_지갑_버전을_증가시킨_이벤트를_발행한다() {
		Wallet wallet = walletWithPoint(0L);
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(pointRecordRepository.save(any(PointRecord.class)))
			.willAnswer(invocation -> invocation.getArgument(0));

		service.charge(1, 1_000L, "charge-version-key");

		ArgumentCaptor<WalletBalanceChangedEvent> eventCaptor = ArgumentCaptor.forClass(WalletBalanceChangedEvent.class);
		then(eventPublisher).should().publishEvent(eventCaptor.capture());
		assertThat(wallet.getProjectionVersion()).isEqualTo(1L);
		assertThat(eventCaptor.getValue().walletVersion()).isEqualTo(1L);
	}

	@Test
	void 충전_금액이_1000원_미만이면_거절한다() {
		assertThatThrownBy(() -> service.charge(1, 999L, "charge-key"))
			.isInstanceOf(InvalidWalletAmountException.class);

		then(walletRepository).shouldHaveNoInteractions();
	}

	@Test
	void 충전으로_지갑_총액이_1조원을_넘으면_거절한다() {
		Wallet wallet = walletWithPoint(950_000_000_000L);
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));

		assertThatThrownBy(() -> service.charge(1, 100_000_000_000L, "balance-limit"))
			.isInstanceOf(InvalidWalletAmountException.class);

		assertThat(wallet.getPoint()).isEqualTo(950_000_000_000L);
		then(pointRecordRepository).should(never()).save(any());
	}

	@Test
	void 환불은_활성_hold를_제외한_가용_잔액까지만_허용한다() {
		Wallet wallet = walletWithPoint(10_000L);
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(wallet.getId())).willReturn(7_000L);
		given(pointRecordRepository.save(any(PointRecord.class)))
			.willAnswer(invocation -> invocation.getArgument(0));

		assertThatThrownBy(() -> service.refund(1, 3_001L, "refund-key"))
			.isInstanceOf(InsufficientAvailableBalanceException.class);

		WalletTransactionResponse response = service.refund(1, 3_000L, "refund-key-2");

		assertThat(wallet.getPoint()).isEqualTo(7_000L);
		assertThat(response.amount()).isEqualTo(-3_000L);
		assertThat(response.balance()).isEqualTo(7_000L);
		then(eventPublisher).should().publishEvent(org.mockito.ArgumentMatchers.any(WalletBalanceChangedEvent.class));
	}

	@Test
	void 정산과_주문취소_환불은_각각_지갑_SSE를_발행한다() {
		Wallet wallet = walletWithPoint(10_000L);
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(wallet.getId())).willReturn(0L);
		given(pointRecordRepository.save(any(PointRecord.class))).willAnswer(invocation -> invocation.getArgument(0));

		service.settle(1, 10, 5_000L);
		service.cancelRefund(1, 10, 3_000L);

		ArgumentCaptor<WalletBalanceChangedEvent> events = ArgumentCaptor.forClass(WalletBalanceChangedEvent.class);
		then(eventPublisher).should(org.mockito.Mockito.times(2)).publishEvent(events.capture());
		assertThat(events.getAllValues()).extracting(WalletBalanceChangedEvent::walletVersion).containsExactly(1L, 2L);
	}

	@Test
	void 동일한_key와_요청은_기존_결과를_반환한다() {
		Wallet wallet = walletWithPoint(10_000L);
		PointRecord existing = PointRecord.charge(
			wallet.getId(),
			10_000L,
			10_000L,
			"same-key"
		);
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(pointRecordRepository.findByWalletIdAndIdempotencyKey(
			wallet.getId(),
			"same-key"
		)).willReturn(Optional.of(existing));

		WalletTransactionResponse response = service.charge(1, 10_000L, "same-key");

		assertThat(response.balance()).isEqualTo(10_000L);
		then(pointRecordRepository).should(never()).save(any());
	}

	@Test
	void 동일한_key를_다른_금액이나_거래_유형에_사용하면_거절한다() {
		Wallet wallet = walletWithPoint(10_000L);
		PointRecord existing = PointRecord.charge(
			wallet.getId(),
			10_000L,
			10_000L,
			"same-key"
		);
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(pointRecordRepository.findByWalletIdAndIdempotencyKey(
			wallet.getId(),
			"same-key"
		)).willReturn(Optional.of(existing));

		assertThatThrownBy(() -> service.charge(1, 20_000L, "same-key"))
			.isInstanceOf(IdempotencyConflictException.class);
		assertThatThrownBy(() -> service.refund(1, 10_000L, "same-key"))
			.isInstanceOf(IdempotencyConflictException.class);
	}

	@Test
	void idempotency_key는_비어_있지_않은_64자_이하여야_한다() {
		assertThatThrownBy(() -> service.charge(1, 1_000L, " "))
			.isInstanceOf(InvalidIdempotencyKeyException.class);
		assertThatThrownBy(() -> service.refund(1, 1_000L, "a".repeat(65)))
			.isInstanceOf(InvalidIdempotencyKeyException.class);

		then(walletRepository).shouldHaveNoInteractions();
	}

	@Test
	void 사용자_지갑이_없으면_거래를_거절한다() {
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.charge(1, 1_000L, "charge-key"))
			.isInstanceOf(WalletNotFoundException.class);
	}

	private Wallet walletWithPoint(long point) {
		Wallet wallet = spy(Wallet.open(1));
		given(wallet.getId()).willReturn(10);
		if (point > 0) {
			wallet.credit(point);
		}
		return wallet;
	}
}
