package com.dbidding.auction.adapter;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.dbidding.auction.port.AuctionCardPort;
import com.dbidding.card.domain.CardMetadata;
import com.dbidding.card.repository.CardMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("!auction-mock")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuctionCardAdapter implements AuctionCardPort {
    private final CardMetadataRepository cardMetadataRepository;

    @Override
    public CardSnapshot getCardSnapshot(Integer itemId) {
        CardMetadata card = cardMetadataRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "카드를 찾을 수 없습니다."));

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
