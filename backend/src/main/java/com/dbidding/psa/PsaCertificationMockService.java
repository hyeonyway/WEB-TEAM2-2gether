package com.dbidding.psa;

import org.springframework.stereotype.Service;

@Service
public class PsaCertificationMockService {

    public PsaCertificationResponse lookup(String certificationNumber) {
        int grade = certificationNumber.charAt(certificationNumber.length() - 1) - '0';
        return new PsaCertificationResponse(
                "psa",
                String.valueOf(grade == 0 ? 10 : grade),
                String.valueOf(1_000 + Math.floorMod(certificationNumber.hashCode(), 9_000))
        );
    }

    public record PsaCertificationResponse(
            String gradeType,
            String psaGrade,
            String population
    ) {
    }
}
