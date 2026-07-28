package com.dbidding.auction.adapter;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.dbidding.auction.port.AuctionCardPort;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    @Override
    public Map<Integer, CardSnapshot> getCardSnapshots(Collection<Integer> itemIds) {
        return itemIds.stream()
                .filter(cards::containsKey)
                .map(cards::get)
                .collect(Collectors.toMap(CardSnapshot::itemId, Function.identity()));
    }

    public void register(CardSnapshot card) {
        cards.put(card.itemId(), card);
    }

    public void clear() {
        cards.clear();
    }
}
