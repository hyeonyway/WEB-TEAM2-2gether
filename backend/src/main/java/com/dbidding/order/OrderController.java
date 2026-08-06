package com.dbidding.order;

import com.dbidding.global.security.CurrentUser;
import com.dbidding.order.dto.OrderResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/purchases")
    public List<OrderResponse> findPurchases(@CurrentUser Integer userId) {
        return orderService.findAllForBuyer(userId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @GetMapping("/sales")
    public List<OrderResponse> findSales(@CurrentUser Integer userId) {
        return orderService.findAllForSeller(userId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @GetMapping("/{orderId}")
    public OrderResponse findOne(@CurrentUser Integer userId, @PathVariable Integer orderId) {
        return OrderResponse.from(orderService.findOne(orderId, userId));
    }

    @PostMapping("/{orderId}/confirm")
    public OrderResponse confirm(@CurrentUser Integer userId, @PathVariable Integer orderId) {
        return OrderResponse.from(orderService.confirm(orderId, userId));
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(@CurrentUser Integer userId, @PathVariable Integer orderId) {
        return OrderResponse.from(orderService.cancel(orderId, userId));
    }

    @PostMapping("/{orderId}/seller-cancel")
    public OrderResponse sellerCancel(@CurrentUser Integer userId, @PathVariable Integer orderId) {
        return OrderResponse.from(orderService.sellerCancel(orderId, userId));
    }
}
