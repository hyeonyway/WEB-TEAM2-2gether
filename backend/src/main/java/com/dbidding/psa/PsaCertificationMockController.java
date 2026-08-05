package com.dbidding.psa;

import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/psa-certifications")
@RequiredArgsConstructor
public class PsaCertificationMockController {
    private final PsaCertificationMockService psaCertificationMockService;

    @GetMapping("/{certificationNumber}")
    public PsaCertificationMockService.PsaCertificationResponse lookup(
            @PathVariable @Pattern(regexp = "\\d{7,10}") String certificationNumber
    ) {
        return psaCertificationMockService.lookup(certificationNumber);
    }
}
