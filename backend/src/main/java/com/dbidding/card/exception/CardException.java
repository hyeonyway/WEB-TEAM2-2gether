package com.dbidding.card.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class CardException extends ApiException {

    private CardException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static CardException notFound() {
        return new CardException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "카드를 찾을 수 없습니다.");
    }
}
