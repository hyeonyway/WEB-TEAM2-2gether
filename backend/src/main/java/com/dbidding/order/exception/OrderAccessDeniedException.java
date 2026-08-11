package com.dbidding.order.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class OrderAccessDeniedException extends ApiException {

    public OrderAccessDeniedException() {
        super(HttpStatus.FORBIDDEN, "ORDER_ACCESS_DENIED", "본인의 주문만 처리할 수 있습니다.");
    }
}
