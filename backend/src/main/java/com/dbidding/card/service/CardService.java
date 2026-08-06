package com.dbidding.card.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.dbidding.card.domain.CardMetadata;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.repository.CardMetadataRepository;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CardService {
    private final CardMetadataRepository cardMetadataRepository;

    public CardSnapshot getCardSnapshot(Integer cardId) {
        CardMetadata card = cardMetadataRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "카드를 찾을 수 없습니다."));
        return toSnapshot(card);
    }

    public Map<Integer, CardSnapshot> getCardSnapshots(Collection<Integer> cardIds) {
        return cardMetadataRepository.findAllById(cardIds).stream()
                .map(this::toSnapshot)
                .collect(Collectors.toMap(CardSnapshot::cardId, Function.identity()));
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
}
