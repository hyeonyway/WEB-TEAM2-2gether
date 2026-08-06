package com.dbidding.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidOrderStatusException extends RuntimeException {

    public InvalidOrderStatusException() {
        super("이미 확정되었거나 취소된 주문입니다.");
    }
}
