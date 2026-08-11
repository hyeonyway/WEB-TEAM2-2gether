package com.dbidding.auction.stream;

public class InvalidBidStreamEventException extends RuntimeException {
    public InvalidBidStreamEventException(String message) {
        super(message);
    }

    public InvalidBidStreamEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
