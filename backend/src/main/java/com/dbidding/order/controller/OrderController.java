package com.dbidding.order.controller;

import com.dbidding.global.security.CurrentUser;
import com.dbidding.order.dto.OrderResponse;
import com.dbidding.order.service.OrderService;
import com.dbidding.order.service.redis.RedisOrderCommandService;
import com.dbidding.order.service.redis.RedisOrderRealtimeStateReader;
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
    private final java.util.Optional<RedisOrderRealtimeStateReader> realtimeStateReader;
    private final java.util.Optional<RedisOrderCommandService> redisOrderCommandService;

    @GetMapping("/purchases")
    public List<OrderResponse> findPurchases(@CurrentUser Integer userId) {
        return realtimeStateReader.map(reader -> reader.findForBuyer(userId))
                .orElseGet(() -> orderService.findAllForBuyer(userId).stream().map(OrderResponse::from).toList());
    }

    @GetMapping("/sales")
    public List<OrderResponse> findSales(@CurrentUser Integer userId) {
        return realtimeStateReader.map(reader -> reader.findForSeller(userId))
                .orElseGet(() -> orderService.findAllForSeller(userId).stream().map(OrderResponse::from).toList());
    }

    @GetMapping("/{orderId}")
    public OrderResponse findOne(@CurrentUser Integer userId, @PathVariable Integer orderId) {
        return OrderResponse.from(orderService.findOne(orderId, userId));
    }

    @PostMapping("/{orderId}/confirm")
    public OrderResponse confirm(@CurrentUser Integer userId, @PathVariable Integer orderId) {
        return redisOrderCommandService.map(service -> service.confirm(orderId, userId))
                .orElseGet(() -> OrderResponse.from(orderService.confirm(orderId, userId)));
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(@CurrentUser Integer userId, @PathVariable Integer orderId) {
        return redisOrderCommandService.map(service -> service.cancel(orderId, userId))
                .orElseGet(() -> OrderResponse.from(orderService.cancel(orderId, userId)));
    }

    @PostMapping("/{orderId}/seller-cancel")
    public OrderResponse sellerCancel(@CurrentUser Integer userId, @PathVariable Integer orderId) {
        return redisOrderCommandService.map(service -> service.sellerCancel(orderId, userId))
                .orElseGet(() -> OrderResponse.from(orderService.sellerCancel(orderId, userId)));
    }
}
