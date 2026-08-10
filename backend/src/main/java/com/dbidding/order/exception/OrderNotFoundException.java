package com.dbidding.order.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class OrderNotFoundException extends ApiException {

    public OrderNotFoundException() {
        super(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다.");
    }
}
