package com.dbidding.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.dbidding.card.domain.CardMetadata;
import com.dbidding.card.repository.CardMetadataRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CardPsaGradeQueryServiceTest {

    @Mock
    private CardMetadataRepository cardMetadataRepository;

    @Mock
    private CardMetadata cardMetadata;

    @InjectMocks
    private CardPsaGradeQueryService service;

    @Test
    void PSA_인증에_필요한_카드_메타데이터를_조회한다() {
        given(cardMetadataRepository.findById(27)).willReturn(Optional.of(cardMetadata));
        given(cardMetadata.getPsaGrade()).willReturn("PSA 10");
        given(cardMetadata.getIssuedYear()).willReturn("2024");
        given(cardMetadata.getCardNumber()).willReturn("SV-P 001");

        assertThat(service.findPsaCardInfo(27))
                .contains(new CardPsaGradeQueryService.PsaCardInfo("PSA 10", "2024", "SV-P 001"));
    }
}
