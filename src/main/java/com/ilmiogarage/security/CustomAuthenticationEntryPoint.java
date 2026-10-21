package com.ilmiogarage.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilmiogarage.dto.common.ApiErrorResponse;
import com.ilmiogarage.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());

        ApiErrorResponse error = ApiErrorResponse.of(
                "Unauthorized",
                HttpStatus.UNAUTHORIZED.value(),
                "Autenticazione richiesta per accedere a questa risorsa.",
                ErrorCode.UNAUTHORIZED.name(),
                request.getRequestURI(),
                null,
                UUID.randomUUID().toString()
        );

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
