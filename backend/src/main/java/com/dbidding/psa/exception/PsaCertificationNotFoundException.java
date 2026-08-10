package com.dbidding.psa.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class PsaCertificationNotFoundException extends ApiException {

    public PsaCertificationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "PSA_CERTIFICATION_NOT_FOUND", "등록된 PSA 인증번호를 찾을 수 없습니다.");
    }
}
