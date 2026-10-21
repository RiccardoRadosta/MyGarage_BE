package com.ilmiogarage.dto.auth;

public record TokenRefreshResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {
    public static TokenRefreshResponse of(String accessToken, String refreshToken, long expiresInSeconds) {
        return new TokenRefreshResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}
