package com.ilmiogarage.dto.vehicle;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ilmiogarage.model.enums.MaintenanceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LastMaintenanceSummary(
        UUID id,
        MaintenanceType type,
        String typeLabel,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate date,
        Integer mileage,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
        BigDecimal cost
) {
}
