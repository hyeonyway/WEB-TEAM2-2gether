package com.dbidding.order.service;

import com.dbidding.order.domain.Order;
import com.dbidding.order.event.OrderCancelledEvent;
import com.dbidding.order.event.OrderCancelledEvent.CancelledBy;
import com.dbidding.order.event.OrderCompletedEvent;
import com.dbidding.order.exception.OrderAccessDeniedException;
import com.dbidding.order.exception.OrderNotFoundException;
import com.dbidding.order.port.OrderEventPort;
import com.dbidding.order.repository.OrderRepository;
import com.dbidding.wallet.service.WalletService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private static final String AUCTION_UNIQUE_CONSTRAINT = "uk_orders_auction";

    private final OrderRepository orderRepository;
    private final WalletService walletService;
    private final OrderEventPort orderEventPort;

    public Order findOne(Integer orderId, Integer currentUserId) {
        Order order = getOrder(orderId);
        requireParticipant(order, currentUserId);
        return order;
    }

    public List<Order> findAllForBuyer(Integer buyerId) {
        return orderRepository.findByBuyerIdOrderByIdDesc(buyerId);
    }

    public List<Order> findAllForSeller(Integer sellerId) {
        return orderRepository.findBySellerIdOrderByIdDesc(sellerId);
    }

    @Transactional
    public Order confirm(Integer orderId, Integer currentUserId) {
        Order order = getOrderForUpdate(orderId);
        requireBuyer(order, currentUserId);

        order.confirm();
        walletService.settle(order.getSellerId(), order.getAuctionId(), order.getPrice());
        orderEventPort.publishCompleted(new OrderCompletedEvent(
                order.getId(), order.getAuctionId(), order.getBuyerId(), order.getSellerId(), order.getCardName()
        ));
        return order;
    }

    @Transactional
    public Order cancel(Integer orderId, Integer currentUserId) {
        Order order = getOrderForUpdate(orderId);
        requireBuyer(order, currentUserId);
        return cancel(order, CancelledBy.BUYER);
    }

    @Transactional
    public Order sellerCancel(Integer orderId, Integer currentUserId) {
        Order order = getOrderForUpdate(orderId);
        requireSeller(order, currentUserId);
        return cancel(order, CancelledBy.SELLER);
    }

    private Order cancel(Order order, CancelledBy cancelledBy) {
        order.cancel();
        walletService.cancelRefund(order.getBuyerId(), order.getAuctionId(), order.getPrice());
        orderEventPort.publishCancelled(new OrderCancelledEvent(
                order.getId(), order.getAuctionId(), order.getBuyerId(), order.getSellerId(),
                order.getCardName(), cancelledBy
        ));
        return order;
    }

    /**
     * {@code auction} 패키지가 auction.event.AuctionClosedEvent를 몰라도 되게 원시값만 받는다.
     * {@code orders.auction_id} 유니크 제약 덕분에 중복 호출돼도 두 번째 저장 시도는 제약
     * 위반으로 걸러지는데, 정확히 그 제약 위반일 때만 무시한다 — FK/NOT NULL 위반 등 다른
     * 무결성 오류까지 "중복이라 무시"로 삼켜버리면 진짜 버그가 조용히 묻힌다(PR #228 CodeRabbit
     * 리뷰). {@code save} 대신 {@code saveAndFlush}를 써서, 제약 위반이 이 트랜잭션의 나중
     * 커밋 시점이 아니라 지금 이 호출에서 바로 터지게 한다.
     */
    @Transactional
    public void createFromAuctionClosed(
            Integer auctionId, Integer winnerId, Integer sellerId, String cardName, long winningPrice
    ) {
        if (winnerId == null) {
            return;
        }
        try {
            orderRepository.saveAndFlush(Order.pendingConfirm(auctionId, winnerId, sellerId, cardName, winningPrice));
        } catch (DataIntegrityViolationException exception) {
            if (!isAuctionUniqueConstraintViolation(exception)) {
                throw exception;
            }
            log.debug("event=order.creation.duplicate_skipped auctionId={}", auctionId, exception);
        }
    }

    private boolean isAuctionUniqueConstraintViolation(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                String constraintName = constraintViolation.getConstraintName();
                if (constraintName == null) {
                    return false;
                }
                String normalizedName = constraintName.replace("`", "");
                String unqualifiedName = normalizedName.substring(normalizedName.lastIndexOf('.') + 1);
                return unqualifiedName.equalsIgnoreCase(AUCTION_UNIQUE_CONSTRAINT);
            }
            cause = cause.getCause();
        }
        return false;
    }

    private Order getOrder(Integer orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
    }

    /**
     * 확정/취소처럼 상태를 바꾸는 진입점에서만 쓴다. 잠금 없이 조회하면 구매자
     * 확정과 판매자 취소가 동시에 들어왔을 때 둘 다 PENDING_CONFIRM을 읽고 통과해
     * 정산/환불과 이벤트 발행이 중복될 수 있다(PR #228 CodeRabbit 리뷰).
     * 행 잠금으로 두 번째 트랜잭션은 첫 번째가 커밋될 때까지 대기했다가, 이미
     * 바뀐 상태를 보고 InvalidOrderStatusException으로 막힌다.
     */
    private Order getOrderForUpdate(Integer orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(OrderNotFoundException::new);
    }

    private void requireBuyer(Order order, Integer currentUserId) {
        if (!order.getBuyerId().equals(currentUserId)) {
            throw new OrderAccessDeniedException();
        }
    }

    private void requireSeller(Order order, Integer currentUserId) {
        if (!order.getSellerId().equals(currentUserId)) {
            throw new OrderAccessDeniedException();
        }
    }

    private void requireParticipant(Order order, Integer currentUserId) {
        if (!order.getBuyerId().equals(currentUserId) && !order.getSellerId().equals(currentUserId)) {
            throw new OrderAccessDeniedException();
        }
    }
}
