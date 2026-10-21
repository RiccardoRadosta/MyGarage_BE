package com.ilmiogarage.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "L'email è obbligatoria.")
        @Email(message = "L'indirizzo email non è valido.")
        String email,

        @NotBlank(message = "La password è obbligatoria.")
        String password
) {
}
