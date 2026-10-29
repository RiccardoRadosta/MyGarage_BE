package com.ilmiogarage.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Richiesta di invio email per recupero password")
public record ForgotPasswordRequest(
        @Schema(description = "Indirizzo email dell'account", example = "utente@example.com")
        @NotBlank(message = "L'email è obbligatoria.")
        @Email(message = "L'indirizzo email non è valido.")
        @Size(max = 255, message = "L'email non può superare 255 caratteri.")
        String email
) {
}
