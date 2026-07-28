package com.dbidding.auction.adapter;

import com.dbidding.auction.port.AuctionCardStatisticPort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("auction-mock")
public class InMemoryAuctionCardStatisticAdapter implements AuctionCardStatisticPort {
    private final List<CardStatisticEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void recordAuctionOpened(Integer itemId, LocalDateTime date) {
        events.add(new CardStatisticEvent(CardStatisticEventType.AUCTION_OPENED, itemId, null, date));
    }

    @Override
    public void recordBid(Integer itemId, LocalDateTime date) {
        events.add(new CardStatisticEvent(CardStatisticEventType.BID_RECORDED, itemId, null, date));
    }

    @Override
    public void recordAuctionCompleted(Integer itemId, long winningPrice, LocalDateTime date) {
        events.add(new CardStatisticEvent(CardStatisticEventType.AUCTION_COMPLETED, itemId, winningPrice, date));
    }

    @Override
    public void recordAuctionClosedWithoutTrade(Integer itemId, LocalDateTime date) {
        events.add(new CardStatisticEvent(CardStatisticEventType.AUCTION_CLOSED_WITHOUT_TRADE, itemId, null, date));
    }

    public List<CardStatisticEvent> recordedEvents() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }

    public record CardStatisticEvent(
            CardStatisticEventType type,
            Integer itemId,
            Long winningPrice,
            LocalDateTime occurredAt
    ) {
    }

    public enum CardStatisticEventType {
        AUCTION_OPENED,
        BID_RECORDED,
        AUCTION_COMPLETED,
        AUCTION_CLOSED_WITHOUT_TRADE
    }
}
