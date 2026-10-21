package com.ilmiogarage.mapper;

import com.ilmiogarage.dto.maintenance.MaintenanceDetailResponse;
import com.ilmiogarage.dto.maintenance.MaintenanceResponse;
import com.ilmiogarage.dto.vehicle.VehicleSummary;
import com.ilmiogarage.model.entity.MaintenanceRecord;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;

@Component
public class MaintenanceMapper {

    private final VehicleMapper vehicleMapper;

    public MaintenanceMapper(VehicleMapper vehicleMapper) {
        this.vehicleMapper = vehicleMapper;
    }

    public MaintenanceResponse toResponse(MaintenanceRecord record) {
        if (record == null) {
            return null;
        }
        return new MaintenanceResponse(
                record.getId(),
                record.getVehicle().getId(),
                record.getType(),
                record.getType().resolveLabel(record.getCustomDescription()),
                record.getCustomDescription(),
                record.getDate(),
                record.getMileage(),
                record.getCost() != null ? record.getCost().setScale(2, RoundingMode.HALF_UP) : null,
                record.getNotes(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }

    public MaintenanceDetailResponse toDetailResponse(MaintenanceRecord record) {
        if (record == null) {
            return null;
        }
        VehicleSummary vehicleSummary = vehicleMapper.toSummary(record.getVehicle());
        return new MaintenanceDetailResponse(
                record.getId(),
                record.getVehicle().getId(),
                record.getType(),
                record.getType().resolveLabel(record.getCustomDescription()),
                record.getCustomDescription(),
                record.getDate(),
                record.getMileage(),
                record.getCost() != null ? record.getCost().setScale(2, RoundingMode.HALF_UP) : null,
                record.getNotes(),
                record.getCreatedAt(),
                record.getUpdatedAt(),
                vehicleSummary
        );
    }
}
