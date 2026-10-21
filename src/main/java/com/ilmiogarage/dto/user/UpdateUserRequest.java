package com.ilmiogarage.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 1, max = 100, message = "Il nome deve essere compreso tra 1 e 100 caratteri.")
        String name,

        @Email(message = "L'indirizzo email non è valido.")
        @Size(max = 255, message = "L'email non può superare 255 caratteri.")
        String email
) {
}
