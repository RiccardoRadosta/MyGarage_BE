package com.ilmiogarage.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Il nome è obbligatorio.")
        @Size(min = 1, max = 100, message = "Il nome deve essere compreso tra 1 e 100 caratteri.")
        String name,

        @NotBlank(message = "L'email è obbligatoria.")
        @Email(message = "L'indirizzo email non è valido.")
        @Size(max = 255, message = "L'email non può superare 255 caratteri.")
        String email,

        @NotBlank(message = "La password è obbligatoria.")
        @Size(min = 8, message = "La password deve contenere almeno 8 caratteri.")
        String password
) {
}
