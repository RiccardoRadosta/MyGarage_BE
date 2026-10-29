package com.ilmiogarage.security;

public record GoogleUserInfo(
        String sub,
        String email,
        boolean emailVerified,
        String name
) {
}
