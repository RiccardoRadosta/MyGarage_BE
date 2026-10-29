package com.ilmiogarage.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Richiesta di autenticazione o registrazione tramite Google Sign-In")
public record GoogleLoginRequest(
        @Schema(description = "ID Token Google (JWT) ottenuto dal flusso di autenticazione mobile", example = "eyJhbGciOiJSUzI1NiIs...", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "L'ID token Google è obbligatorio.")
        String idToken
) {
}
