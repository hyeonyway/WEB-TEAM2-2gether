package com.dbidding.order.adapter;

import com.dbidding.order.port.WalletSettlementPort;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 실제 지갑 잔액 이동은 이번 라운드 범위 밖이다(wallet 담당과 협의 후 별도 구현 예정,
 * {@code docs/hamin/order/1-purchase-confirm-cancel-plan.md} 4절 참고). 실제 wallets.point는
 * 건드리지 않고, 호출된 사실을 로그 + 메모리 기록으로만 남겨서 구매확정/구매취소 흐름
 * 자체(상태 전이, 이벤트 발행)를 테스트/확인할 수 있게 한다. 기록은 최근 {@value #MAX_RECORDS}건까지만
 * 보관한다 — 무제한 누적되면 애플리케이션 수명 동안 힙이 계속 늘어난다(PR #228 CodeRabbit 리뷰).
 */
@Slf4j
@Component
public class MockWalletSettlementAdapter implements WalletSettlementPort {

    private static final int MAX_RECORDS = 1_000;

    public enum Action {PAYOUT_TO_SELLER, REFUND_TO_BUYER}

    public record SettlementRecord(Action action, Integer userId, Integer orderId, long amount) {
    }

    private final Deque<SettlementRecord> records = new ArrayDeque<>();

    @Override
    public void payoutToSeller(Integer sellerId, Integer orderId, long amount) {
        record(new SettlementRecord(Action.PAYOUT_TO_SELLER, sellerId, orderId, amount));
        log.info("event=order.settlement.mock action=PAYOUT_TO_SELLER sellerId={} orderId={} amount={}", sellerId, orderId, amount);
    }

    @Override
    public void refundToBuyer(Integer buyerId, Integer orderId, long amount) {
        record(new SettlementRecord(Action.REFUND_TO_BUYER, buyerId, orderId, amount));
        log.info("event=order.settlement.mock action=REFUND_TO_BUYER buyerId={} orderId={} amount={}", buyerId, orderId, amount);
    }

    public synchronized List<SettlementRecord> getRecords() {
        return List.copyOf(records);
    }

    private synchronized void record(SettlementRecord settlementRecord) {
        if (records.size() == MAX_RECORDS) {
            records.removeFirst();
        }
        records.addLast(settlementRecord);
    }
}
