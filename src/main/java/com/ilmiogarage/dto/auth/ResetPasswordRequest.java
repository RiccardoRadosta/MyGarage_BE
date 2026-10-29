package com.ilmiogarage.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Richiesta di reimpostazione della password tramite token monouso")
public record ResetPasswordRequest(
        @Schema(description = "Token monouso ricevuto via email", example = "4k7Lx9Q...")
        @NotBlank(message = "Il token è obbligatorio.")
        String token,

        @Schema(description = "Nuova password (minimo 8 caratteri)", example = "NuovaPassword123!")
        @NotBlank(message = "La nuova password è obbligatoria.")
        @Size(min = 8, message = "La password deve contenere almeno 8 caratteri.")
        String newPassword,

        @Schema(description = "Conferma della nuova password", example = "NuovaPassword123!")
        @NotBlank(message = "La conferma password è obbligatoria.")
        String confirmPassword
) {
}
