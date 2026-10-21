package com.ilmiogarage.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldErrorDto(
        String field,
        String code,
        String message
) {
}
