package com.ilmiogarage.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ilmiogarage.model.enums.MaintenanceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceResponse(
        UUID id,
        UUID vehicleId,
        MaintenanceType type,
        String typeLabel,
        String customDescription,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate date,
        Integer mileage,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
        BigDecimal cost,
        String notes,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant updatedAt,
        Boolean hasAttachment
) {
    public MaintenanceResponse(UUID id, UUID vehicleId, MaintenanceType type, String typeLabel,
                               String customDescription, LocalDate date, Integer mileage,
                               BigDecimal cost, String notes, Instant createdAt, Instant updatedAt) {
        this(id, vehicleId, type, typeLabel, customDescription, date, mileage, cost, notes, createdAt, updatedAt, false);
    }
}
