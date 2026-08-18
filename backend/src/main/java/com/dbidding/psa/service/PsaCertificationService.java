package com.dbidding.psa.service;

import com.dbidding.card.service.CardPsaGradeQueryService;
import com.dbidding.psa.domain.PsaCertificationFixture;
import com.dbidding.psa.exception.PsaCertificationNotFoundException;
import com.dbidding.psa.repository.PsaCertificationFixtureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PsaCertificationService {

    private final PsaCertificationFixtureRepository fixtureRepository;
    private final CardPsaGradeQueryService cardPsaGradeQueryService;

    public PsaCertificationResponse lookup(String certificationNumber) {
        PsaCertificationFixture fixture = fixtureRepository.findByCertificationNumber(certificationNumber)
                .orElseThrow(PsaCertificationNotFoundException::new);
        CardPsaGradeQueryService.PsaCardInfo card = cardPsaGradeQueryService.findPsaCardInfo(fixture.getItemId())
                .orElseThrow(PsaCertificationNotFoundException::new);
        return new PsaCertificationResponse(
                fixture.getItemId(),
                "psa",
                normalizeGrade(card.psaGrade()),
                population(certificationNumber),
                card.issuedYear(),
                card.cardNumber()
        );
    }

    public PsaCertificationSampleResponse sample() {
        PsaCertificationFixture fixture = fixtureRepository.findFirstByOrderByIdAsc()
                .orElseThrow(PsaCertificationNotFoundException::new);
        return new PsaCertificationSampleResponse(fixture.getCertificationNumber());
    }

    private String normalizeGrade(String psaGrade) {
        return psaGrade.replaceFirst("(?i)^PSA\\s*", "").trim();
    }

    private String population(String certificationNumber) {
        return String.valueOf(1_000 + Math.floorMod(certificationNumber.hashCode(), 9_000));
    }

    public record PsaCertificationResponse(
            Integer itemId,
            String gradeType,
            String psaGrade,
            String population,
            String issuedYear,
            String cardNumber
    ) {
    }

    public record PsaCertificationSampleResponse(String certificationNumber) {
    }
}
