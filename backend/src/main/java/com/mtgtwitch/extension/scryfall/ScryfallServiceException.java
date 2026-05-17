package com.mtgtwitch.extension.scryfall;

public class ScryfallServiceException extends RuntimeException {

    public ScryfallServiceException(String message) {
        super(message);
    }

    public ScryfallServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
