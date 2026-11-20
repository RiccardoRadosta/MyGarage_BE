package com.ilmiogarage.exception;

import org.springframework.http.HttpStatus;

public class UpgradeRequiredException extends ApiException {

    public UpgradeRequiredException(String message) {
        super(HttpStatus.FORBIDDEN, ErrorCode.UPGRADE_REQUIRED, message);
    }
}
