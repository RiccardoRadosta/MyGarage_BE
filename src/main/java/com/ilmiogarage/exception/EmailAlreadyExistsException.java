package com.ilmiogarage.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends ApiException {
    public EmailAlreadyExistsException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.EMAIL_ALREADY_EXISTS, message);
    }
}
