package com.dbidding.card.service;

import com.dbidding.card.exception.CardException;
import com.dbidding.card.domain.CardMetadata;
import com.dbidding.card.domain.CardTheme;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.dto.CardResponses.StatisticCardSnapshot;
import com.dbidding.card.repository.CardMetadataRepository;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CardService {
    private final CardMetadataRepository cardMetadataRepository;

    public CardSnapshot getCardSnapshot(Integer cardId) {
        CardMetadata card = cardMetadataRepository.findById(cardId)
                .orElseThrow(CardException::notFound);
        return toSnapshot(card);
    }

    public Map<Integer, CardSnapshot> getCardSnapshots(Collection<Integer> cardIds) {
        return cardMetadataRepository.findAllById(cardIds).stream()
                .map(this::toSnapshot)
                .collect(Collectors.toMap(CardSnapshot::cardId, Function.identity()));
    }

    public Map<Integer, StatisticCardSnapshot> getStatisticCardSnapshots(Collection<Integer> cardIds) {
        return cardMetadataRepository.findAllById(cardIds).stream()
                .map(this::toStatisticSnapshot)
                .collect(Collectors.toMap(StatisticCardSnapshot::cardId, Function.identity()));
    }

    private CardSnapshot toSnapshot(CardMetadata card) {
        return new CardSnapshot(
                card.getId(),
                card.getName(),
                card.getCardSet().getName(),
                card.getPsaGrade(),
                card.getLanguage(),
                card.getImagePath()
        );
    }

    private StatisticCardSnapshot toStatisticSnapshot(CardMetadata card) {
        return new StatisticCardSnapshot(
                card.getId(),
                card.getName(),
                CardTheme.from(card),
                card.getImagePath()
        );
    }
}
