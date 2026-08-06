package com.dbidding.order.adapter;

import com.dbidding.order.port.WalletSettlementPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 실제 지갑 잔액 이동은 이번 라운드 범위 밖이다(wallet 담당과 협의 후 별도 구현 예정,
 * {@code docs/hamin/order/1-purchase-confirm-cancel-plan.md} 4절 참고). Order의 상태 전이와
 * 이벤트 발행은 정상 동작하도록 이 자리에 로그만 남기는 임시 구현을 둔다.
 */
@Slf4j
@Component
public class NoOpWalletSettlementAdapter implements WalletSettlementPort {

    @Override
    public void payoutToSeller(Integer sellerId, Integer orderId, long amount) {
        log.info("event=order.settlement.noop role=seller sellerId={} orderId={} amount={}", sellerId, orderId, amount);
    }

    @Override
    public void refundToBuyer(Integer buyerId, Integer orderId, long amount) {
        log.info("event=order.settlement.noop role=buyer buyerId={} orderId={} amount={}", buyerId, orderId, amount);
    }
}
