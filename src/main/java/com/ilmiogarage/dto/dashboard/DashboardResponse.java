package com.ilmiogarage.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ilmiogarage.dto.maintenance.MaintenanceResponse;
import com.ilmiogarage.dto.vehicle.VehicleResponse;
import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        int vehicleCount,
        long maintenanceCount,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
        BigDecimal totalSpent,
        List<VehicleResponse> vehicles,
        List<MaintenanceResponse> recentMaintenances
) {
}
