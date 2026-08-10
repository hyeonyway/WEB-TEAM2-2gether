package com.dbidding.psa.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PsaCertificationNotFoundException extends RuntimeException {

    public PsaCertificationNotFoundException() {
        super("등록된 PSA 인증번호를 찾을 수 없습니다.");
    }
}
