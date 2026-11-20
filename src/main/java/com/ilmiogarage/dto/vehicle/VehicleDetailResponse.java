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
        boolean isPrimary,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant updatedAt,
        LastMaintenanceSummary lastMaintenance,
        long maintenanceCount
) {
    public VehicleDetailResponse(UUID id, VehicleIcon icon, String brand, String model, Integer year,
                                 String plate, Integer mileage, Instant createdAt, Instant updatedAt,
                                 LastMaintenanceSummary lastMaintenance, long maintenanceCount) {
        this(id, icon, brand, model, year, plate, mileage, false, createdAt, updatedAt, lastMaintenance, maintenanceCount);
    }
}
