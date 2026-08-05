package com.dbidding.psa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PsaCertificationMockServiceTest {

    private final PsaCertificationMockService service = new PsaCertificationMockService();

    @Test
    void 인증번호_마지막_숫자로_모의_PSA_등급을_반환한다() {
        assertThat(service.lookup("1234567890").psaGrade()).isEqualTo("10");
        assertThat(service.lookup("1234567897").psaGrade()).isEqualTo("7");
    }

    @Test
    void 모의_조회는_판매_등록_폼에_적용할_값을_반환한다() {
        var response = service.lookup("1234567");

        assertThat(response.gradeType()).isEqualTo("psa");
        assertThat(response.population()).isNotBlank();
    }
}
