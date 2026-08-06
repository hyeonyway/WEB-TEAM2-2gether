package com.dbidding.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class OrderAccessDeniedException extends RuntimeException {

    public OrderAccessDeniedException() {
        super("본인의 주문만 처리할 수 있습니다.");
    }
}
