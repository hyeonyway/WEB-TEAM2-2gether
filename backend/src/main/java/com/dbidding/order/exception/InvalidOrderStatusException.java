package com.dbidding.order.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class InvalidOrderStatusException extends ApiException {

    public InvalidOrderStatusException() {
        super(HttpStatus.CONFLICT, "INVALID_ORDER_STATUS", "이미 확정되었거나 취소된 주문입니다.");
    }
}
