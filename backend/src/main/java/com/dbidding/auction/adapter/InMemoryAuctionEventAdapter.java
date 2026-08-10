package com.dbidding.auction.adapter;

import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionOpenedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.auction.event.AuctionEventPublisher;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("auction-mock")
public class InMemoryAuctionEventAdapter implements AuctionEventPublisher {
    private final List<AuctionOpenedEvent> openedEvents = new CopyOnWriteArrayList<>();
    private final List<BidPlacedEvent> bidPlacedEvents = new CopyOnWriteArrayList<>();
    private final List<AuctionClosedEvent> closedEvents = new CopyOnWriteArrayList<>();

    @Override
    public void publishOpened(AuctionOpenedEvent event) {
        openedEvents.add(event);
    }

    @Override
    public void publishBidPlaced(BidPlacedEvent event) {
        bidPlacedEvents.add(event);
    }

    @Override
    public void publishClosed(AuctionClosedEvent event) {
        closedEvents.add(event);
    }

    public List<AuctionOpenedEvent> publishedOpenedEvents() {
        return List.copyOf(openedEvents);
    }

    public List<BidPlacedEvent> publishedBidPlacedEvents() {
        return List.copyOf(bidPlacedEvents);
    }

    public List<AuctionClosedEvent> publishedClosedEvents() {
        return List.copyOf(closedEvents);
    }

    public void clear() {
        openedEvents.clear();
        bidPlacedEvents.clear();
        closedEvents.clear();
    }
}
