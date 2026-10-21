package com.ilmiogarage.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "Il refresh token è obbligatorio.")
        String refreshToken
) {
}
