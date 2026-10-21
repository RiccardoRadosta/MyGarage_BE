package com.ilmiogarage.exception;

import org.springframework.http.HttpStatus;

public class InvalidDateException extends ApiException {
    public InvalidDateException(String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_DATE, message);
    }
}
