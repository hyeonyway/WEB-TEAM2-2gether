package com.dbidding.wallet.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dbidding.auction.port.WalletPort.WalletSnapshot;
import com.dbidding.wallet.domain.HoldStatus;
import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.domain.WalletHold;
import com.dbidding.wallet.exception.InsufficientAvailableBalanceException;
import com.dbidding.wallet.exception.InvalidWalletHoldStateException;
import com.dbidding.wallet.exception.WalletNotFoundException;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
class AuctionWalletAdapterTest {

	@Mock
	private WalletRepository walletRepository;

	@Mock
	private WalletHoldRepository walletHoldRepository;

	@Mock
	private PointRecordRepository pointRecordRepository;

	private AuctionWalletAdapter adapter;

	@BeforeEach
	void setUp() {
		adapter = new AuctionWalletAdapter(
			walletRepository,
			walletHoldRepository,
			pointRecordRepository
		);
	}

	@Test
	void 신규_hold는_다른_경매_동결액을_포함해_가용잔액을_계산한다() {
		Wallet wallet = walletWithPoint(20_000L);
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(wallet.getId())).willReturn(3_000L);
		given(walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
			wallet.getId(),
			20
		)).willReturn(Optional.empty());

		WalletSnapshot result = adapter.holdBidAmount(1, 20, 11_000L);

		assertThat(result.frozenBalance()).isEqualTo(14_000L);
		assertThat(result.availableBalance()).isEqualTo(6_000L);
		then(walletHoldRepository).should().save(
			org.mockito.ArgumentMatchers.argThat(hold ->
				hold.getWalletId().equals(wallet.getId())
					&& hold.getAuctionId().equals(20)
					&& hold.getAmount() == 11_000L
					&& hold.getStatus() == HoldStatus.HELD
			)
		);
	}

	@Test
	void 재입찰은_기존_hold와의_차액만_가용잔액에서_검증한다() {
		Wallet wallet = walletWithPoint(20_000L);
		WalletHold hold = WalletHold.held(wallet.getId(), 20, 11_000L);
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(wallet.getId())).willReturn(11_000L);
		given(walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
			wallet.getId(),
			20
		)).willReturn(Optional.of(hold));

		WalletSnapshot result = adapter.holdBidAmount(1, 20, 16_000L);

		assertThat(hold.getAmount()).isEqualTo(16_000L);
		assertThat(result.availableBalance()).isEqualTo(4_000L);
		assertThat(result.frozenBalance()).isEqualTo(16_000L);
		then(walletHoldRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void 증가분보다_가용잔액이_적으면_hold를_변경하지_않는다() {
		Wallet wallet = walletWithPoint(20_000L);
		WalletHold hold = WalletHold.held(wallet.getId(), 20, 11_000L);
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(wallet.getId())).willReturn(19_000L);
		given(walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
			wallet.getId(),
			20
		)).willReturn(Optional.of(hold));

		assertThatThrownBy(() -> adapter.holdBidAmount(1, 20, 13_000L))
			.isInstanceOf(InsufficientAvailableBalanceException.class);
		assertThat(hold.getAmount()).isEqualTo(11_000L);
		then(walletHoldRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void 기존_hold보다_낮은_재입찰_금액은_상태_예외로_거절한다() {
		Wallet wallet = walletWithPoint(20_000L);
		WalletHold hold = WalletHold.held(wallet.getId(), 20, 11_000L);
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(wallet.getId())).willReturn(11_000L);
		given(walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
			wallet.getId(),
			20
		)).willReturn(Optional.of(hold));

		assertThatThrownBy(() -> adapter.holdBidAmount(1, 20, 10_000L))
			.isInstanceOf(InvalidWalletHoldStateException.class);
		assertThat(hold.getAmount()).isEqualTo(11_000L);
		then(walletHoldRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void RELEASED_뒤_재입찰하면_새_HELD를_저장한다() {
		Wallet wallet = walletWithPoint(20_000L);
		WalletHold released = WalletHold.held(wallet.getId(), 20, 11_000L);
		released.release(Instant.parse("2026-07-29T00:00:00Z"));
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(wallet.getId())).willReturn(0L);
		given(walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
			wallet.getId(),
			20
		)).willReturn(Optional.of(released));

		WalletSnapshot result = adapter.holdBidAmount(1, 20, 16_000L);

		assertThat(released.getStatus()).isEqualTo(HoldStatus.RELEASED);
		assertThat(result.frozenBalance()).isEqualTo(16_000L);
		then(walletHoldRepository).should().save(
			org.mockito.ArgumentMatchers.argThat(hold ->
				hold != released
					&& hold.getAmount() == 16_000L
					&& hold.getStatus() == HoldStatus.HELD
			)
		);
	}

	@Test
	void 상회입찰_해제는_HELD만_RELEASED로_바꾸고_중복호출은_no_op이다() {
		Wallet wallet = walletWithPoint(20_000L);
		WalletHold hold = WalletHold.held(wallet.getId(), 20, 11_000L);
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
		given(walletRepository.sumHeldAmount(wallet.getId())).willReturn(11_000L, 0L);
		given(walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
			wallet.getId(),
			20
		)).willReturn(Optional.of(hold));

		WalletSnapshot first = adapter.releaseBidHold(1, 20);
		WalletSnapshot second = adapter.releaseBidHold(1, 20);

		assertThat(hold.getStatus()).isEqualTo(HoldStatus.RELEASED);
		assertThat(first.frozenBalance()).isZero();
		assertThat(second.frozenBalance()).isZero();
		then(walletHoldRepository).should(never()).delete(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void Wallet이_없으면_조회와_변경을_거절한다() {
		given(walletRepository.findByUserId(1)).willReturn(Optional.empty());
		given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.empty());

		assertThatThrownBy(() -> adapter.getWallet(1))
			.isInstanceOf(WalletNotFoundException.class);
		assertThatThrownBy(() -> adapter.holdBidAmount(1, 20, 11_000L))
			.isInstanceOf(WalletNotFoundException.class);
	}

	private Wallet walletWithPoint(long point) {
		Wallet wallet = spy(Wallet.open(1));
		given(wallet.getId()).willReturn(10);
		wallet.credit(point);
		return wallet;
	}
}
