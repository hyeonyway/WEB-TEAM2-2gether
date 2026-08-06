package com.dbidding.order;

import com.dbidding.order.event.OrderCancelledEvent;
import com.dbidding.order.event.OrderCompletedEvent;
import com.dbidding.order.exception.OrderAccessDeniedException;
import com.dbidding.order.exception.OrderNotFoundException;
import com.dbidding.order.port.WalletSettlementPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final WalletSettlementPort walletSettlementPort;
    private final ApplicationEventPublisher eventPublisher;

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
        Order order = getOrder(orderId);
        requireBuyer(order, currentUserId);

        order.confirm();
        walletSettlementPort.payoutToSeller(order.getSellerId(), order.getId(), order.getPrice());
        eventPublisher.publishEvent(new OrderCompletedEvent(
                order.getId(), order.getAuctionId(), order.getBuyerId(), order.getSellerId()
        ));
        return order;
    }

    @Transactional
    public Order cancel(Integer orderId, Integer currentUserId) {
        Order order = getOrder(orderId);
        requireBuyer(order, currentUserId);

        order.cancel();
        walletSettlementPort.refundToBuyer(order.getBuyerId(), order.getId(), order.getPrice());
        eventPublisher.publishEvent(new OrderCancelledEvent(
                order.getId(), order.getAuctionId(), order.getBuyerId(), order.getSellerId()
        ));
        return order;
    }

    private Order getOrder(Integer orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
    }

    private void requireBuyer(Order order, Integer currentUserId) {
        if (!order.getBuyerId().equals(currentUserId)) {
            throw new OrderAccessDeniedException();
        }
    }

    private void requireParticipant(Order order, Integer currentUserId) {
        if (!order.getBuyerId().equals(currentUserId) && !order.getSellerId().equals(currentUserId)) {
            throw new OrderAccessDeniedException();
        }
    }
}
