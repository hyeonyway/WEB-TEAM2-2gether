package com.dbidding.order.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.dbidding.global.security.CurrentUserProvider;
import com.dbidding.order.exception.InvalidOrderStatusException;
import com.dbidding.order.exception.OrderAccessDeniedException;
import com.dbidding.order.exception.OrderNotFoundException;
import com.dbidding.order.domain.Order;
import com.dbidding.order.domain.OrderStatus;
import com.dbidding.order.service.OrderService;
import com.dbidding.order.service.redis.RedisOrderRealtimeStateReader;
import com.dbidding.order.dto.OrderResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final Integer BUYER_ID = 1;
    private static final Integer SELLER_ID = 2;
    private static final Integer AUCTION_ID = 10;
    private static final Integer ORDER_ID = 100;
    private static final String CARD_NAME = "리자몽";
    private static final long PRICE = 50_000L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;
    @MockitoBean
    private RedisOrderRealtimeStateReader realtimeStateReader;

    @BeforeEach
    void setUp() {
        given(currentUserProvider.getCurrentUserId()).willReturn(BUYER_ID);
    }

    private Order order() {
        return Order.pendingConfirm(AUCTION_ID, BUYER_ID, SELLER_ID, CARD_NAME, PRICE);
    }

    @Test
    void 내_구매_목록을_조회한다() throws Exception {
        given(realtimeStateReader.findForBuyer(BUYER_ID)).willReturn(List.of(OrderResponse.from(order())));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/purchases"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].auction_id").value(AUCTION_ID))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].card_name").value(CARD_NAME));
    }

    @Test
    void 내_판매_목록을_조회한다() throws Exception {
        given(realtimeStateReader.findForSeller(BUYER_ID)).willReturn(List.of(OrderResponse.from(order())));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/sales"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1));
    }

    @Test
    void Redis_주문_상태를_생성일_내림차순으로_조회한다() throws Exception {
        given(realtimeStateReader.findForBuyer(BUYER_ID)).willReturn(List.of(
                new OrderResponse(null, 11, "뮤", 70_000L, OrderStatus.PENDING_CONFIRM,
                        Instant.parse("2026-08-11T01:00:00Z"), "2-0"),
                new OrderResponse(null, AUCTION_ID, CARD_NAME, PRICE, OrderStatus.PENDING_CONFIRM,
                        Instant.parse("2026-08-10T01:00:00Z"), "1-0")
        ));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/purchases"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].auction_id").value(11))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].auction_id").value(AUCTION_ID));
    }

    @Test
    void 주문_상세를_조회한다() throws Exception {
        given(orderService.findOne(ORDER_ID, BUYER_ID)).willReturn(order());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/{orderId}", ORDER_ID))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("PENDING_CONFIRM"));
    }

    @Test
    void 존재하지_않는_주문은_공통_오류_응답으로_반환한다() throws Exception {
        given(orderService.findOne(ORDER_ID, BUYER_ID)).willThrow(new OrderNotFoundException());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/{orderId}", ORDER_ID))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("주문을 찾을 수 없습니다."));
    }

    @Test
    void 권한이_없는_주문_요청은_공통_오류_응답으로_반환한다() throws Exception {
        given(orderService.confirm(ORDER_ID, BUYER_ID)).willThrow(new OrderAccessDeniedException());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/orders/{orderId}/confirm", ORDER_ID))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("ORDER_ACCESS_DENIED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("본인의 주문만 처리할 수 있습니다."));
    }

    @Test
    void 처리할_수_없는_주문_상태는_공통_오류_응답으로_반환한다() throws Exception {
        given(orderService.cancel(ORDER_ID, BUYER_ID)).willThrow(new InvalidOrderStatusException());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/orders/{orderId}/cancel", ORDER_ID))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("INVALID_ORDER_STATUS"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("이미 확정되었거나 취소된 주문입니다."));
    }

    @Test
    void 구매확정을_요청하면_확정된_주문을_반환한다() throws Exception {
        Order confirmed = order();
        confirmed.confirm();
        given(orderService.confirm(ORDER_ID, BUYER_ID)).willReturn(confirmed);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/orders/{orderId}/confirm", ORDER_ID))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("COMPLETED"));

        verify(orderService).confirm(ORDER_ID, BUYER_ID);
    }

    @Test
    void 구매취소를_요청하면_취소된_주문을_반환한다() throws Exception {
        Order cancelled = order();
        cancelled.cancel();
        given(orderService.cancel(ORDER_ID, BUYER_ID)).willReturn(cancelled);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/orders/{orderId}/cancel", ORDER_ID))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CANCELLED"));

        verify(orderService).cancel(ORDER_ID, BUYER_ID);
    }

    @Test
    void 판매취소를_요청하면_취소된_주문을_반환한다() throws Exception {
        Order cancelled = order();
        cancelled.cancel();
        given(orderService.sellerCancel(ORDER_ID, BUYER_ID)).willReturn(cancelled);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/orders/{orderId}/seller-cancel", ORDER_ID))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CANCELLED"));

        verify(orderService).sellerCancel(ORDER_ID, BUYER_ID);
    }
}
