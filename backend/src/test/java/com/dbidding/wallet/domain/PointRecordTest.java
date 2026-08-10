package com.dbidding.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PointRecordTest {

	@Test
	void 충전과_주문_정산_취소_환불은_양수이고_출금_거래는_음수로_기록된다() {
		PointRecord charge = PointRecord.charge(1, 10_000L, 10_000L, "charge-key");
		PointRecord refund = PointRecord.refund(1, 3_000L, 7_000L, "refund-key");
		PointRecord capture = PointRecord.auctionCapture(1, 20, 5_000L, 2_000L);
		PointRecord settlement = PointRecord.orderSettlement(2, 20, 5_000L, 5_000L);
		PointRecord cancelRefund = PointRecord.orderCancelRefund(1, 20, 5_000L, 7_000L);

		assertThat(charge.getTransactionType()).isEqualTo(PointTransactionType.CHARGE);
		assertThat(charge.getAmount()).isEqualTo(10_000L);
		assertThat(refund.getTransactionType()).isEqualTo(PointTransactionType.REFUND);
		assertThat(refund.getAmount()).isEqualTo(-3_000L);
		assertThat(capture.getTransactionType()).isEqualTo(PointTransactionType.AUCTION_CAPTURE);
		assertThat(capture.getAmount()).isEqualTo(-5_000L);
		assertThat(capture.getAuctionId()).isEqualTo(20);
		assertThat(settlement.getTransactionType()).isEqualTo(PointTransactionType.ORDER_SETTLEMENT);
		assertThat(settlement.getAmount()).isEqualTo(5_000L);
		assertThat(settlement.getAuctionId()).isEqualTo(20);
		assertThat(cancelRefund.getTransactionType()).isEqualTo(PointTransactionType.ORDER_CANCEL_REFUND);
		assertThat(cancelRefund.getAmount()).isEqualTo(5_000L);
		assertThat(cancelRefund.getAuctionId()).isEqualTo(20);
	}

	@Test
	void 거래_금액은_양수여야_한다() {
		assertThatThrownBy(() -> PointRecord.charge(1, 0L, 0L, "charge-key"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PointRecord.refund(1, -1L, 0L, "refund-key"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PointRecord.auctionCapture(1, 20, 0L, 0L))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PointRecord.orderSettlement(1, 20, 0L, 0L))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PointRecord.orderCancelRefund(1, 20, -1L, 0L))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
