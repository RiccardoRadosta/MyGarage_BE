package com.ilmiogarage.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String type,
        String title,
        int status,
        String detail,
        String code,
        String instance,
        List<FieldErrorDto> fieldErrors,
        String traceId
) {
    public static ApiErrorResponse of(String title, int status, String detail, String code, String instance,
                                      List<FieldErrorDto> fieldErrors, String traceId) {
        return new ApiErrorResponse(
                "about:blank",
                title,
                status,
                detail,
                code,
                instance,
                fieldErrors,
                traceId
        );
    }
}
