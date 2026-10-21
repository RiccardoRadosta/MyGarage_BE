package com.ilmiogarage.mapper;

import com.ilmiogarage.dto.vehicle.LastMaintenanceSummary;
import com.ilmiogarage.dto.vehicle.VehicleDetailResponse;
import com.ilmiogarage.dto.vehicle.VehicleResponse;
import com.ilmiogarage.dto.vehicle.VehicleSummary;
import com.ilmiogarage.model.entity.MaintenanceRecord;
import com.ilmiogarage.model.entity.Vehicle;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;

@Component
public class VehicleMapper {

    public VehicleResponse toResponse(Vehicle vehicle, LastMaintenanceSummary lastMaintenance) {
        if (vehicle == null) {
            return null;
        }
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getIcon(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicle.getPlate(),
                vehicle.getMileage(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt(),
                lastMaintenance
        );
    }

    public VehicleDetailResponse toDetailResponse(Vehicle vehicle, LastMaintenanceSummary lastMaintenance, long count) {
        if (vehicle == null) {
            return null;
        }
        return new VehicleDetailResponse(
                vehicle.getId(),
                vehicle.getIcon(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicle.getPlate(),
                vehicle.getMileage(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt(),
                lastMaintenance,
                count
        );
    }

    public VehicleSummary toSummary(Vehicle vehicle) {
        if (vehicle == null) {
            return null;
        }
        return new VehicleSummary(
                vehicle.getId(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getPlate(),
                vehicle.getIcon()
        );
    }

    public LastMaintenanceSummary toLastMaintenanceSummary(MaintenanceRecord record) {
        if (record == null) {
            return null;
        }
        return new LastMaintenanceSummary(
                record.getId(),
                record.getType(),
                record.getType().resolveLabel(record.getCustomDescription()),
                record.getDate(),
                record.getMileage(),
                record.getCost() != null ? record.getCost().setScale(2, RoundingMode.HALF_UP) : null
        );
    }
}
