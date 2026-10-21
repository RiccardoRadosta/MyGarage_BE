package com.ilmiogarage.dto.vehicle;

import com.ilmiogarage.model.enums.VehicleIcon;
import java.util.UUID;

public record VehicleSummary(
        UUID vehicleId,
        String brand,
        String model,
        String plate,
        VehicleIcon icon
) {
}
