package com.dbidding.order;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.dbidding.global.security.CurrentUserProvider;
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
    private static final long PRICE = 50_000L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @BeforeEach
    void setUp() {
        given(currentUserProvider.getCurrentUserId()).willReturn(BUYER_ID);
    }

    private Order order() {
        return Order.pendingConfirm(AUCTION_ID, BUYER_ID, SELLER_ID, PRICE);
    }

    @Test
    void 내_구매_목록을_조회한다() throws Exception {
        given(orderService.findAllForBuyer(BUYER_ID)).willReturn(List.of(order()));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/purchases"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].auction_id").value(AUCTION_ID));
    }

    @Test
    void 내_판매_목록을_조회한다() throws Exception {
        given(orderService.findAllForSeller(BUYER_ID)).willReturn(List.of(order()));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/sales"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1));
    }

    @Test
    void 주문_상세를_조회한다() throws Exception {
        given(orderService.findOne(ORDER_ID, BUYER_ID)).willReturn(order());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/{orderId}", ORDER_ID))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("PENDING_CONFIRM"));
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
}
