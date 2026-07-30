package com.dbidding.wallet.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dbidding.auction.port.WalletPort.WalletSnapshot;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.service.WalletService;

@ExtendWith(MockitoExtension.class)
class AuctionWalletAdapterTest {

	@Mock
	private WalletService walletService;

	private AuctionWalletAdapter adapter;

	@BeforeEach
	void setUp() {
		adapter = new AuctionWalletAdapter(walletService);
	}

	@Test
	void 잔액_조회는_WalletService에_위임하고_Auction_응답으로_변환한다() {
		given(walletService.getBalance(1))
			.willReturn(new WalletBalanceResponse(20_000L, 3_000L, 17_000L));

		WalletSnapshot result = adapter.getWallet(1);

		assertThat(result.availableBalance()).isEqualTo(17_000L);
		assertThat(result.frozenBalance()).isEqualTo(3_000L);
		then(walletService).should().getBalance(1);
	}

	@Test
	void 홀드는_WalletService에_위임한다() {
		given(walletService.hold(1, 20, 16_000L))
			.willReturn(new WalletBalanceResponse(20_000L, 16_000L, 4_000L));

		WalletSnapshot result = adapter.holdBidAmount(1, 20, 16_000L);

		assertThat(result.availableBalance()).isEqualTo(4_000L);
		assertThat(result.frozenBalance()).isEqualTo(16_000L);
		then(walletService).should().hold(1, 20, 16_000L);
	}

	@Test
	void 홀드_해제는_WalletService에_위임한다() {
		given(walletService.release(1, 20))
			.willReturn(new WalletBalanceResponse(20_000L, 0L, 20_000L));

		WalletSnapshot result = adapter.releaseBidHold(1, 20);

		assertThat(result.availableBalance()).isEqualTo(20_000L);
		assertThat(result.frozenBalance()).isZero();
		then(walletService).should().release(1, 20);
	}

	@Test
	void 낙찰_차감은_WalletService에_위임한다() {
		given(walletService.capture(1, 20, 16_000L))
			.willReturn(new WalletBalanceResponse(4_000L, 0L, 4_000L));

		WalletSnapshot result = adapter.confirmWinningBid(1, 20, 16_000L);

		assertThat(result.availableBalance()).isEqualTo(4_000L);
		assertThat(result.frozenBalance()).isZero();
		then(walletService).should().capture(1, 20, 16_000L);
	}
}
