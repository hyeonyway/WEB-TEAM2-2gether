package com.dbidding.psa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.dbidding.card.service.CardPsaGradeQueryService;
import com.dbidding.psa.domain.PsaCertificationFixture;
import com.dbidding.psa.exception.PsaCertificationNotFoundException;
import com.dbidding.psa.repository.PsaCertificationFixtureRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PsaCertificationServiceTest {

    @Mock
    private PsaCertificationFixtureRepository fixtureRepository;

    @Mock
    private CardPsaGradeQueryService cardPsaGradeQueryService;

    @InjectMocks
    private PsaCertificationService service;

    @Test
    void 등록된_인증번호는_연결된_카드의_등급과_itemId를_반환한다() {
        PsaCertificationFixture fixture = new PsaCertificationFixture("12345678", 27);
        given(fixtureRepository.findByCertificationNumber("12345678")).willReturn(Optional.of(fixture));
        given(cardPsaGradeQueryService.findPsaCardInfo(27))
                .willReturn(Optional.of(new CardPsaGradeQueryService.PsaCardInfo("PSA 10", "2024", "SV-P 001")));

        PsaCertificationService.PsaCertificationResponse response = service.lookup("12345678");

        assertThat(response.itemId()).isEqualTo(27);
        assertThat(response.gradeType()).isEqualTo("psa");
        assertThat(response.psaGrade()).isEqualTo("10");
        assertThat(response.population()).matches("\\d{4}");
        assertThat(response.issuedYear()).isEqualTo("2024");
        assertThat(response.cardNumber()).isEqualTo("SV-P 001");
    }

    @Test
    void 미등록_인증번호는_찾을수없음_예외를_던진다() {
        given(fixtureRepository.findByCertificationNumber("12345678")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.lookup("12345678"))
                .isInstanceOf(PsaCertificationNotFoundException.class);
    }

    @Test
    void sample은_가장_먼저_등록된_인증번호를_반환한다() {
        given(fixtureRepository.findFirstByOrderByIdAsc())
                .willReturn(Optional.of(new PsaCertificationFixture("12345678", 27)));

        assertThat(service.sample().certificationNumber()).isEqualTo("12345678");
    }

    @Test
    void fixture가_없으면_sample도_찾을수없음_예외를_던진다() {
        given(fixtureRepository.findFirstByOrderByIdAsc()).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.sample())
                .isInstanceOf(PsaCertificationNotFoundException.class);
    }
}
