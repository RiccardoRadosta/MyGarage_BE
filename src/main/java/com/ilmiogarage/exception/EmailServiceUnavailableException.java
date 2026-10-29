package com.ilmiogarage.exception;

public class EmailServiceUnavailableException extends RuntimeException {

    public EmailServiceUnavailableException(String message) {
        super(message);
    }
}
