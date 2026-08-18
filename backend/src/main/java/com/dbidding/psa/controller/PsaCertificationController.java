package com.dbidding.psa.controller;

import com.dbidding.psa.service.PsaCertificationService;
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
public class PsaCertificationController {

    private final PsaCertificationService psaCertificationService;

    @GetMapping("/sample")
    public PsaCertificationService.PsaCertificationSampleResponse sample() {
        return psaCertificationService.sample();
    }

    @GetMapping("/{certificationNumber}")
    public PsaCertificationService.PsaCertificationResponse lookup(
            @PathVariable @Pattern(regexp = "\\d{7,10}") String certificationNumber
    ) {
        return psaCertificationService.lookup(certificationNumber);
    }
}
