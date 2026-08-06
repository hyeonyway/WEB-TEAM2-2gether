package com.dbidding.order.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.order.adapter.MockWalletSettlementAdapter.Action;
import com.dbidding.order.adapter.MockWalletSettlementAdapter.SettlementRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockWalletSettlementAdapterTest {

    private final MockWalletSettlementAdapter adapter = new MockWalletSettlementAdapter();

    @Test
    void 판매자_정산을_호출하면_기록에_남는다() {
        adapter.payoutToSeller(2, 100, 50_000L);

        assertThat(adapter.getRecords())
                .containsExactly(new SettlementRecord(Action.PAYOUT_TO_SELLER, 2, 100, 50_000L));
    }

    @Test
    void 구매자_환불을_호출하면_기록에_남는다() {
        adapter.refundToBuyer(1, 100, 50_000L);

        assertThat(adapter.getRecords())
                .containsExactly(new SettlementRecord(Action.REFUND_TO_BUYER, 1, 100, 50_000L));
    }

    @Test
    void 여러_번_호출하면_호출_순서대로_전부_쌓인다() {
        adapter.payoutToSeller(2, 100, 50_000L);
        adapter.refundToBuyer(1, 101, 30_000L);

        List<SettlementRecord> records = adapter.getRecords();

        assertThat(records).hasSize(2);
        assertThat(records.get(0).action()).isEqualTo(Action.PAYOUT_TO_SELLER);
        assertThat(records.get(1).action()).isEqualTo(Action.REFUND_TO_BUYER);
    }
}
