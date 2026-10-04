package com.eunoia.identity.domain;

public class InvalidProfileImageException extends RuntimeException {
    public InvalidProfileImageException(String message) {
        super(message);
    }

    public InvalidProfileImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
