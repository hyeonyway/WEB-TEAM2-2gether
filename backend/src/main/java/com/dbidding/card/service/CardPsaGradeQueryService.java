package com.dbidding.card.service;

import com.dbidding.card.domain.CardMetadata;
import com.dbidding.card.repository.CardMetadataRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CardPsaGradeQueryService {

    private final CardMetadataRepository cardMetadataRepository;

    public Optional<PsaCardInfo> findPsaCardInfo(Integer itemId) {
        return cardMetadataRepository.findById(itemId)
                .map(card -> new PsaCardInfo(card.getPsaGrade(), card.getIssuedYear(), card.getCardNumber()));
    }

    public record PsaCardInfo(String psaGrade, String issuedYear, String cardNumber) {
    }
}
