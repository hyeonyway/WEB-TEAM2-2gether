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
    void 카드의_PSA_등급만_조회한다() {
        given(cardMetadataRepository.findById(27)).willReturn(Optional.of(cardMetadata));
        given(cardMetadata.getPsaGrade()).willReturn("PSA 10");

        assertThat(service.findPsaGrade(27)).contains("PSA 10");
    }
}
