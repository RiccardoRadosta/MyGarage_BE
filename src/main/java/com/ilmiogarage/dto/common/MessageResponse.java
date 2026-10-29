package com.ilmiogarage.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Risposta generica con messaggio informativo")
public record MessageResponse(
        @Schema(description = "Messaggio di esito dell'operazione", example = "Operazione completata con successo.")
        String message
) {
}
