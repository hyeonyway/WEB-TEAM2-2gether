package com.dbidding.order.port;

import com.dbidding.order.event.OrderCancelledEvent;
import com.dbidding.order.event.OrderCompletedEvent;

public interface OrderEventPort {

    void publishCompleted(OrderCompletedEvent event);

    void publishCancelled(OrderCancelledEvent event);
}
