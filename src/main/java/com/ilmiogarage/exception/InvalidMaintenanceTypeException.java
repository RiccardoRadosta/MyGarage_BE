package com.ilmiogarage.exception;

import org.springframework.http.HttpStatus;

public class InvalidMaintenanceTypeException extends ApiException {
    public InvalidMaintenanceTypeException(String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_MAINTENANCE_TYPE, message);
    }
}
