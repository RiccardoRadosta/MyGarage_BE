package com.ilmiogarage.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ilmiogarage.model.enums.MaintenanceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateMaintenanceRequest(
        @NotNull(message = "Il tipo di manutenzione è obbligatorio.")
        MaintenanceType type,

        @Size(max = 100, message = "La descrizione personalizzata non può superare 100 caratteri.")
        String customDescription,

        @NotNull(message = "La data dell'intervento è obbligatoria.")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate date,

        @NotNull(message = "Il chilometraggio è obbligatorio.")
        @Min(value = 0, message = "Il chilometraggio deve essere maggiore o uguale a zero.")
        Integer mileage,

        @NotNull(message = "Il costo è obbligatorio.")
        @DecimalMin(value = "0.00", message = "Il costo non può essere negativo.")
        @Digits(integer = 10, fraction = 2, message = "Il costo può avere al massimo due cifre decimali.")
        BigDecimal cost,

        @Size(max = 2000, message = "Le note possono contenere al massimo 2000 caratteri.")
        String notes
) {
}
