package com.dbidding.auction.adapter;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.dbidding.auction.port.AuctionCardPort;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("auction-mock")
public class MockAuctionCardAdapter implements AuctionCardPort {
    private final Map<Integer, CardSnapshot> cards = new ConcurrentHashMap<>();

    public MockAuctionCardAdapter() {
        register(new CardSnapshot(
                1,
                "Mock Pikachu Promo",
                "Mock Set",
                "10",
                "JP",
                "/mock/cards/pikachu.png"
        ));
    }

    @Override
    public CardSnapshot getCardSnapshot(Integer itemId) {
        CardSnapshot card = cards.get(itemId);
        if (card == null) {
            throw new ResponseStatusException(NOT_FOUND, "카드를 찾을 수 없습니다.");
        }
        return card;
    }

    public void register(CardSnapshot card) {
        cards.put(card.itemId(), card);
    }

    public void clear() {
        cards.clear();
    }
}
