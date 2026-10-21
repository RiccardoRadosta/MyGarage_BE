package com.ilmiogarage.dto.vehicle;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ilmiogarage.model.enums.VehicleIcon;
import java.time.Instant;
import java.util.UUID;

public record VehicleDetailResponse(
        UUID id,
        VehicleIcon icon,
        String brand,
        String model,
        Integer year,
        String plate,
        Integer mileage,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant updatedAt,
        LastMaintenanceSummary lastMaintenance,
        long maintenanceCount
) {
}
