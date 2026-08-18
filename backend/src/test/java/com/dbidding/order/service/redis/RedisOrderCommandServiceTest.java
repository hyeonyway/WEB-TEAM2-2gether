package com.dbidding.order.service.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.order.domain.OrderStatus;
import com.dbidding.order.event.OrderCompletedEvent;
import com.dbidding.order.port.OrderEventPort;
import com.dbidding.wallet.service.redis.RedisWalletStateSeeder;
import com.dbidding.wallet.sse.WalletBalanceChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisOrderCommandServiceTest {

    @Test
    void 구매확정_Redis_승인_직후_주문알림과_지갑_SSE_이벤트를_발행한다() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") RedisScript<String> transitionScript = mock(RedisScript.class);
        @SuppressWarnings("unchecked") RedisScript<String> readScript = mock(RedisScript.class);
        RedisOrderStateSeeder orderSeeder = mock(RedisOrderStateSeeder.class);
        RedisWalletStateSeeder walletSeeder = mock(RedisWalletStateSeeder.class);
        OrderEventPort orderEvents = mock(OrderEventPort.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC);
        RedisOrderCommandService service = new RedisOrderCommandService(
                redis, transitionScript, orderSeeder, walletSeeder, readScript, new ObjectMapper(), clock,
                orderEvents, events);
        when(redis.execute(eq(readScript), eq(List.of("order:state:by-order-id:100"))))
                .thenReturn("{\"orderId\":\"100\",\"auctionId\":\"10\",\"buyerId\":\"1\",\"sellerId\":\"7\","
                        + "\"cardName\":\"리자몽\",\"price\":\"5.0000e+4\",\"createdAt\":\"2026-08-11T00:00:00Z\"}");
        when(redis.execute(eq(transitionScript), eq(List.of(
                        "order:state:10", "wallet:balance:7", "order:idempotency:100:confirm:100", "event:timeline",
                        "order:state:by-order-id:100")),
                eq("1"), eq("COMPLETED"), eq("order.completed.v1"), eq("100"), eq("10"),
                eq("confirm:100"), eq("order.completed.v1:1"), anyString(), eq("2026-08-12T00:00:00Z"),
                eq("1000000000000")))
                .thenReturn("ACCEPTED|1-0|COMPLETED|1|1.00000000000000e+14|6.0000e+4|0|7|false");

        var response = service.confirm(100, 1);

        assertThat(response.status()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderEvents).publishCompleted(argThat((OrderCompletedEvent event) ->
                event.orderId().equals(100) && event.buyerId().equals(1) && event.sellerId().equals(7)));
        verify(events).publishEvent(argThat((Object event) -> event instanceof WalletBalanceChangedEvent changed
                && changed.userId().equals(7)
                && changed.balance().availableBalance() == 60_000L
                && changed.walletVersion() == 100_000_000_000_000L));
    }
}
