package com.dbidding.order.adapter;

import com.dbidding.order.event.OrderCancelledEvent;
import com.dbidding.order.event.OrderCompletedEvent;
import com.dbidding.order.port.OrderEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringOrderEventAdapter implements OrderEventPort {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishCompleted(OrderCompletedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publishCancelled(OrderCancelledEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
