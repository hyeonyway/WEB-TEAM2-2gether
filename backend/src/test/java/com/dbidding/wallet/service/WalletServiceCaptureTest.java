package com.dbidding.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.dbidding.wallet.domain.HoldStatus;
import com.dbidding.wallet.domain.PointTransactionType;
import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.domain.WalletHold;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.exception.InvalidWalletBalanceException;
import com.dbidding.wallet.exception.InvalidWalletHoldStateException;
import com.dbidding.wallet.metrics.WalletMetrics;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class WalletServiceCaptureTest {

	@Mock
	private WalletRepository walletRepository;

	@Mock
	private WalletHoldRepository walletHoldRepository;

	@Mock
	private PointRecordRepository pointRecordRepository;

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
	void 낙찰하면_hold를_CAPTURED로_바꾸고_총잔액을_실제_차감한다() {
		Wallet wallet = walletWithPoint(20_000L);
		WalletHold hold = WalletHold.held(wallet.getId(), 20, 16_000L);
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(wallet.getId())).willReturn(16_000L);
		given(walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
			wallet.getId(),
			20
		)).willReturn(Optional.of(hold));

		WalletBalanceResponse result = service.capture(1, 20, 16_000L);

		assertThat(wallet.getPoint()).isEqualTo(4_000L);
		assertThat(hold.getStatus()).isEqualTo(HoldStatus.CAPTURED);
		assertThat(result.availableBalance()).isEqualTo(4_000L);
		assertThat(result.frozenBalance()).isZero();
		then(pointRecordRepository).should().save(
			org.mockito.ArgumentMatchers.argThat(record ->
				record.getWalletId().equals(wallet.getId())
					&& record.getAuctionId().equals(20)
					&& record.getTransactionType() == PointTransactionType.AUCTION_CAPTURE
					&& record.getAmount() == -16_000L
					&& record.getBalance() == 4_000L
			)
		);
		then(eventPublisher).should().publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
	}

	@Test
	void 같은_트랜잭션에서_예치한_즉시낙찰은_지갑_행을_다시_잠그지_않는다() {
		Wallet wallet = walletWithPoint(20_000L);
		WalletHold hold = WalletHold.held(wallet.getId(), 20, 16_000L);
		given(walletRepository.findByUserId(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(wallet.getId())).willReturn(16_000L);
		given(walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
			wallet.getId(),
			20
		)).willReturn(Optional.of(hold));

		WalletBalanceResponse result = service.captureAfterHold(1, 20, 16_000L);

		assertThat(wallet.getPoint()).isEqualTo(4_000L);
		assertThat(hold.getStatus()).isEqualTo(HoldStatus.CAPTURED);
		assertThat(result.frozenBalance()).isZero();
		then(walletRepository).should().findByUserId(1);
		then(walletRepository).should(never()).findByUserIdForUpdate(1);
	}

	@Test
	void 이미_CAPTURED인_같은_금액은_재차감하지_않는다() {
		Wallet wallet = walletWithPoint(4_000L);
		WalletHold captured = WalletHold.held(wallet.getId(), 20, 16_000L);
		captured.capture(Instant.parse("2026-07-29T00:00:00Z"));
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(wallet.getId())).willReturn(0L);
		given(walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
			wallet.getId(),
			20
		)).willReturn(Optional.of(captured));

		WalletBalanceResponse result = service.capture(1, 20, 16_000L);

		assertThat(wallet.getPoint()).isEqualTo(4_000L);
		assertThat(result.availableBalance()).isEqualTo(4_000L);
		assertThat(result.frozenBalance()).isZero();
		assertThat(captured.getStatus()).isEqualTo(HoldStatus.CAPTURED);
		then(pointRecordRepository).should(never()).save(
			org.mockito.ArgumentMatchers.any()
		);
	}

	@Test
	void HELD_금액과_확정_금액이_다르면_거절한다() {
		Wallet wallet = walletWithPoint(20_000L);
		WalletHold hold = WalletHold.held(wallet.getId(), 20, 16_000L);
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(wallet.getId())).willReturn(16_000L);
		given(walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
			wallet.getId(),
			20
		)).willReturn(Optional.of(hold));

		assertThatThrownBy(() -> service.capture(1, 20, 15_000L))
			.isInstanceOf(InvalidWalletHoldStateException.class);
		assertThat(wallet.getPoint()).isEqualTo(20_000L);
		assertThat(hold.getStatus()).isEqualTo(HoldStatus.HELD);
		then(pointRecordRepository).should(never()).save(
			org.mockito.ArgumentMatchers.any()
		);
	}

	@Test
	void RELEASED_hold는_확정할_수_없다() {
		Wallet wallet = walletWithPoint(20_000L);
		WalletHold released = WalletHold.held(wallet.getId(), 20, 16_000L);
		released.release(Instant.parse("2026-07-29T00:00:00Z"));
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(wallet.getId())).willReturn(0L);
		given(walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
			wallet.getId(),
			20
		)).willReturn(Optional.of(released));

		assertThatThrownBy(() -> service.capture(1, 20, 16_000L))
			.isInstanceOf(InvalidWalletHoldStateException.class);
		assertThat(wallet.getPoint()).isEqualTo(20_000L);
		assertThat(released.getStatus()).isEqualTo(HoldStatus.RELEASED);
		then(pointRecordRepository).should(never()).save(
			org.mockito.ArgumentMatchers.any()
		);
	}

	@Test
	void 동결액이_총잔액보다_크면_차감하기_전에_손상된_잔액으로_거절한다() {
		Wallet wallet = walletWithPoint(20_000L);
		WalletHold hold = WalletHold.held(wallet.getId(), 20, 16_000L);
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(wallet.getId())).willReturn(21_000L);
		given(walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
			wallet.getId(),
			20
		)).willReturn(Optional.of(hold));

		assertThatThrownBy(() -> service.capture(1, 20, 16_000L))
			.isInstanceOf(InvalidWalletBalanceException.class);

		assertThat(wallet.getPoint()).isEqualTo(20_000L);
		assertThat(hold.getStatus()).isEqualTo(HoldStatus.HELD);
		then(pointRecordRepository).should(never()).save(
			org.mockito.ArgumentMatchers.any()
		);
	}

	private Wallet walletWithPoint(long point) {
		Wallet wallet = spy(Wallet.open(1));
		given(wallet.getId()).willReturn(10);
		wallet.credit(point);
		return wallet;
	}
}
