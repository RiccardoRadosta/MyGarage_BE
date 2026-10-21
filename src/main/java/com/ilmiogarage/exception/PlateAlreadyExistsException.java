package com.ilmiogarage.exception;

import org.springframework.http.HttpStatus;

public class PlateAlreadyExistsException extends ApiException {
    public PlateAlreadyExistsException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.PLATE_ALREADY_EXISTS, message);
    }
}
