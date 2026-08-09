package com.dbidding.psa;

import com.dbidding.card.domain.CardMetadata;
import com.dbidding.card.repository.CardMetadataRepository;
import com.dbidding.psa.domain.PsaCertificationFixture;
import com.dbidding.psa.exception.PsaCertificationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PsaCertificationService {

    private final PsaCertificationFixtureRepository fixtureRepository;
    private final CardMetadataRepository cardMetadataRepository;

    public PsaCertificationResponse lookup(String certificationNumber) {
        PsaCertificationFixture fixture = fixtureRepository.findByCertificationNumber(certificationNumber)
                .orElseThrow(PsaCertificationNotFoundException::new);
        CardMetadata card = cardMetadataRepository.findById(fixture.getItemId())
                .orElseThrow(PsaCertificationNotFoundException::new);
        return new PsaCertificationResponse(
                fixture.getItemId(),
                "psa",
                normalizeGrade(card.getPsaGrade()),
                population(certificationNumber)
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
            String population
    ) {
    }

    public record PsaCertificationSampleResponse(String certificationNumber) {
    }
}
