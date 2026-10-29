package com.ilmiogarage.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Rate limit intercetta e blocca richieste ripetute abusive su /auth/forgot-password con HTTP 429")
    void rateLimiter_blocksForgotPassword_whenLimitExceeded() throws ServletException, IOException {
        RateLimitingFilter filter = new RateLimitingFilter(true, 2, objectMapper);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/v1/auth/forgot-password");
        req1.setRemoteAddr("192.168.1.50");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilterInternal(req1, res1, chain);
        assertThat(res1.getStatus()).isEqualTo(200);

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/auth/forgot-password");
        req2.setRemoteAddr("192.168.1.50");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilterInternal(req2, res2, chain);
        assertThat(res2.getStatus()).isEqualTo(200);

        MockHttpServletRequest req3 = new MockHttpServletRequest("POST", "/api/v1/auth/forgot-password");
        req3.setRemoteAddr("192.168.1.50");
        MockHttpServletResponse res3 = new MockHttpServletResponse();
        filter.doFilterInternal(req3, res3, chain);

        // La 3ª richiesta supera la soglia di 2 req/min e viene bloccata con 429
        assertThat(res3.getStatus()).isEqualTo(429);
        assertThat(res3.getContentAsString()).contains("Troppi tentativi di autenticazione");
    }

    @Test
    @DisplayName("Rate limit intercetta e blocca richieste ripetute abusive su /auth/reset-password con HTTP 429")
    void rateLimiter_blocksResetPassword_whenLimitExceeded() throws ServletException, IOException {
        RateLimitingFilter filter = new RateLimitingFilter(true, 1, objectMapper);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/v1/auth/reset-password");
        req1.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilterInternal(req1, res1, chain);
        assertThat(res1.getStatus()).isEqualTo(200);

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/auth/reset-password");
        req2.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilterInternal(req2, res2, chain);

        // 2ª richiesta bloccata con 429
        assertThat(res2.getStatus()).isEqualTo(429);
    }
}
