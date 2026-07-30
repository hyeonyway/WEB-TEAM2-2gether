package com.dbidding.card.adapter;

import com.dbidding.card.domain.CardMetadata;
import com.dbidding.card.domain.CardTheme;
import com.dbidding.card.repository.CardMetadataRepository;
import com.dbidding.home.port.HomeCardPort;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HomeCardAdapter implements HomeCardPort {
    private final CardMetadataRepository cardRepository;

    @Override
    public Map<Integer, CardSnapshot> getCards(Collection<Integer> cardIds) {
        return cardRepository.findAllById(cardIds).stream()
                .map(this::toSnapshot)
                .collect(Collectors.toMap(CardSnapshot::cardId, Function.identity()));
    }

    private CardSnapshot toSnapshot(CardMetadata card) {
        return new CardSnapshot(
                card.getId(),
                card.getName(),
                CardTheme.from(card),
                card.getImagePath()
        );
    }
}
