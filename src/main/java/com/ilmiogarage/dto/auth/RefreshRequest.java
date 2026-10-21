package com.ilmiogarage.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "Il refresh token è obbligatorio.")
        String refreshToken
) {
}
