package com.ilmiogarage.exception;

import com.ilmiogarage.dto.common.ApiErrorResponse;
import com.ilmiogarage.dto.common.FieldErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.warn("API Exception [{}]: status={}, code={}, message={}",
                traceId, ex.getStatus(), ex.getErrorCode(), ex.getMessage());

        List<FieldErrorDto> fieldErrors = null;
        if (ex instanceof ValidationException ve && !ve.getFieldErrors().isEmpty()) {
            fieldErrors = ve.getFieldErrors();
        }

        ApiErrorResponse response = ApiErrorResponse.of(
                ex.getStatus().getReasonPhrase(),
                ex.getStatus().value(),
                ex.getMessage(),
                ex.getErrorCode().name(),
                request.getRequestURI(),
                fieldErrors,
                traceId
        );
        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        List<FieldErrorDto> fieldErrors = new ArrayList<>();

        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new FieldErrorDto(
                    fe.getField(),
                    fe.getCode(),
                    fe.getDefaultMessage()
            ));
        }

        log.warn("Validation failed [{}]: {} errors on path {}", traceId, fieldErrors.size(), request.getRequestURI());

        ApiErrorResponse response = ApiErrorResponse.of(
                "Validation failed",
                HttpStatus.BAD_REQUEST.value(),
                "Uno o più campi non sono validi.",
                ErrorCode.VALIDATION_ERROR.name(),
                request.getRequestURI(),
                fieldErrors,
                traceId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        List<FieldErrorDto> fieldErrors = new ArrayList<>();

        ex.getConstraintViolations().forEach(cv -> {
            String property = cv.getPropertyPath().toString();
            if (property.contains(".")) {
                property = property.substring(property.lastIndexOf('.') + 1);
            }
            fieldErrors.add(new FieldErrorDto(
                    property,
                    cv.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                    cv.getMessage()
            ));
        });

        ApiErrorResponse response = ApiErrorResponse.of(
                "Validation failed",
                HttpStatus.BAD_REQUEST.value(),
                "Parametri non validi.",
                ErrorCode.VALIDATION_ERROR.name(),
                request.getRequestURI(),
                fieldErrors,
                traceId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        String message = "Il corpo della richiesta è mancante o non è un formato JSON valido.";
        String code = ErrorCode.VALIDATION_ERROR.name();

        Throwable cause = ex.getMostSpecificCause();
        if (cause != null && cause.getMessage() != null) {
            String detail = cause.getMessage();
            if (detail.contains("MaintenanceType") || detail.contains("Tipo di manutenzione")) {
                code = ErrorCode.INVALID_MAINTENANCE_TYPE.name();
                message = "Tipo di manutenzione non valido.";
            } else if (detail.contains("VehicleIcon") || detail.contains("Icona veicolo")) {
                code = ErrorCode.VALIDATION_ERROR.name();
                message = "Icona veicolo non valida. Valori ammessi: carFront, car, motorbike, scooter, truck, van";
            } else if (detail.contains("LocalDate") || detail.contains("date")) {
                code = ErrorCode.INVALID_DATE.name();
                message = "Formato data non valido. Usare formato ISO YYYY-MM-DD.";
            }
        }

        log.warn("Not readable body [{}]: {}", traceId, ex.getMessage());

        ApiErrorResponse response = ApiErrorResponse.of(
                "Bad Request",
                HttpStatus.BAD_REQUEST.value(),
                message,
                code,
                request.getRequestURI(),
                null,
                traceId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        String paramName = ex.getName();
        String message = "Parametro non valido: " + paramName;
        String code = ErrorCode.VALIDATION_ERROR.name();
        HttpStatus status = HttpStatus.BAD_REQUEST;

        if ("vehicleId".equals(paramName) || "maintenanceId".equals(paramName)) {
            status = HttpStatus.NOT_FOUND;
            code = ErrorCode.RESOURCE_NOT_FOUND.name();
            message = "Risorsa non trovata.";
        }

        ApiErrorResponse response = ApiErrorResponse.of(
                status.getReasonPhrase(),
                status.value(),
                message,
                code,
                request.getRequestURI(),
                null,
                traceId
        );
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.warn("Authentication failed [{}]: {}", traceId, ex.getMessage());

        ApiErrorResponse response = ApiErrorResponse.of(
                "Unauthorized",
                HttpStatus.UNAUTHORIZED.value(),
                "Credenziali non valide o sessione scaduta.",
                ErrorCode.UNAUTHORIZED.name(),
                request.getRequestURI(),
                null,
                traceId
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.warn("Access denied [{}]: {}", traceId, ex.getMessage());

        ApiErrorResponse response = ApiErrorResponse.of(
                "Forbidden",
                HttpStatus.FORBIDDEN.value(),
                "Accesso non autorizzato alla risorsa richiesta.",
                ErrorCode.UNAUTHORIZED.name(),
                request.getRequestURI(),
                null,
                traceId
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        ApiErrorResponse response = ApiErrorResponse.of(
                "Not Found",
                HttpStatus.NOT_FOUND.value(),
                "L'endpoint richiesto non esiste.",
                ErrorCode.RESOURCE_NOT_FOUND.name(),
                request.getRequestURI(),
                null,
                traceId
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(org.springframework.web.multipart.MaxUploadSizeExceededException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.warn("File upload exceeds max size limit [{}]: {}", traceId, ex.getMessage());

        ApiErrorResponse response = ApiErrorResponse.of(
                "Payload Too Large",
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "La dimensione dell'allegato supera il limite massimo consentito di 5 MB.",
                ErrorCode.FILE_TOO_LARGE.name(),
                request.getRequestURI(),
                null,
                traceId
        );
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.error("Unhandled internal error [{}]:", traceId, ex);

        ApiErrorResponse response = ApiErrorResponse.of(
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Si è verificato un errore interno del server.",
                ErrorCode.INTERNAL_ERROR.name(),
                request.getRequestURI(),
                null,
                traceId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString();
    }
}
