package com.dbidding.wishlist.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class WishlistException extends ApiException {

    private WishlistException() {
        super(HttpStatus.CONFLICT, "WISHLIST_ALREADY_EXISTS", "이미 찜한 카드입니다.");
    }

    public static WishlistException alreadyExists() {
        return new WishlistException();
    }
}
