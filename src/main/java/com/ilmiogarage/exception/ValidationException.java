package com.ilmiogarage.exception;

import com.ilmiogarage.dto.common.FieldErrorDto;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;

public class ValidationException extends ApiException {

    private final List<FieldErrorDto> fieldErrors;

    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
        this.fieldErrors = Collections.emptyList();
    }

    public ValidationException(String message, List<FieldErrorDto> fieldErrors) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
        this.fieldErrors = fieldErrors != null ? fieldErrors : Collections.emptyList();
    }

    public ValidationException(String field, String code, String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
        this.fieldErrors = List.of(new FieldErrorDto(field, code, message));
    }

    public List<FieldErrorDto> getFieldErrors() {
        return fieldErrors;
    }
}
