package com.dbidding.auction.adapter;

import com.dbidding.auction.port.AuctionEventPort;
import com.dbidding.auction.event.AuctionEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("auction-mock")
public class InMemoryAuctionEventAdapter implements AuctionEventPort {
    private final List<AuctionEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void publish(AuctionEvent event) {
        events.add(event);
    }

    public List<AuctionEvent> publishedEvents() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }
}
