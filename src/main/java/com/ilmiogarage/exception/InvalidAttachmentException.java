package com.ilmiogarage.exception;

import org.springframework.http.HttpStatus;

public class InvalidAttachmentException extends ApiException {

    public InvalidAttachmentException(String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_FILE_FORMAT, message);
    }

    public InvalidAttachmentException(ErrorCode errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
}
