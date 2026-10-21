package com.ilmiogarage.dto.vehicle;

import com.ilmiogarage.model.enums.VehicleIcon;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVehicleRequest(
        @NotNull(message = "L'icona del veicolo è obbligatoria.")
        VehicleIcon icon,

        @NotBlank(message = "La marca è obbligatoria.")
        @Size(min = 1, max = 80, message = "La marca deve essere compresa tra 1 e 80 caratteri.")
        String brand,

        @NotBlank(message = "Il modello è obbligatorio.")
        @Size(min = 1, max = 100, message = "Il modello deve essere compreso tra 1 e 100 caratteri.")
        String model,

        @NotNull(message = "L'anno è obbligatorio.")
        @Min(value = 1900, message = "L'anno deve essere maggiore o uguale al 1900.")
        @Max(value = 2100, message = "L'anno non può superare il 2100.")
        Integer year,

        @NotBlank(message = "La targa è obbligatoria.")
        @Size(min = 1, max = 30, message = "La targa deve essere compresa tra 1 e 30 caratteri.")
        String plate,

        @NotNull(message = "Il chilometraggio è obbligatorio.")
        @Min(value = 0, message = "Il chilometraggio deve essere maggiore o uguale a zero.")
        Integer mileage
) {
}
