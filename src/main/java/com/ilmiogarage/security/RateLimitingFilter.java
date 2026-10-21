package com.ilmiogarage.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilmiogarage.dto.common.ApiErrorResponse;
import com.ilmiogarage.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final boolean enabled;
    private final int maxRequestsPerMinute;
    private final ObjectMapper objectMapper;
    private final Map<String, RequestCounter> requestCounts = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.auth-requests-per-minute:20}") int maxRequestsPerMinute,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (isRateLimitedPath(path)) {
            String clientIp = getClientIp(request);
            log.info("Auth attempt received from IP: {} on path: {}", clientIp, path);

            if (isRateLimited(clientIp)) {
                log.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());

                ApiErrorResponse error = ApiErrorResponse.of(
                        "Too Many Requests",
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        "Troppi tentativi di autenticazione. Riprova più tardi.",
                        ErrorCode.VALIDATION_ERROR.name(),
                        path,
                        null,
                        UUID.randomUUID().toString()
                );
                response.getWriter().write(objectMapper.writeValueAsString(error));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimitedPath(String path) {
        return path != null && (path.endsWith("/auth/login") || path.endsWith("/auth/register"));
    }

    private boolean isRateLimited(String key) {
        long currentMinute = System.currentTimeMillis() / 60000;
        RequestCounter counter = requestCounts.compute(key, (k, existing) -> {
            if (existing == null || existing.minute != currentMinute) {
                return new RequestCounter(currentMinute, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        // Periodic cleanup
        if (requestCounts.size() > 5000) {
            requestCounts.entrySet().removeIf(e -> e.getValue().minute < currentMinute);
        }

        return counter.count.get() > maxRequestsPerMinute;
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class RequestCounter {
        final long minute;
        final AtomicInteger count;

        RequestCounter(long minute, AtomicInteger count) {
            this.minute = minute;
            this.count = count;
        }
    }
}
