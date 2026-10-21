package com.ilmiogarage.mapper;

import com.ilmiogarage.dto.maintenance.MaintenanceDetailResponse;
import com.ilmiogarage.dto.maintenance.MaintenanceResponse;
import com.ilmiogarage.dto.vehicle.VehicleSummary;
import com.ilmiogarage.model.entity.MaintenanceRecord;
import com.ilmiogarage.repository.MaintenanceAttachmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;

@Component
public class MaintenanceMapper {

    private final VehicleMapper vehicleMapper;
    private final MaintenanceAttachmentRepository attachmentRepository;

    @Autowired
    public MaintenanceMapper(VehicleMapper vehicleMapper, MaintenanceAttachmentRepository attachmentRepository) {
        this.vehicleMapper = vehicleMapper;
        this.attachmentRepository = attachmentRepository;
    }

    public MaintenanceMapper(VehicleMapper vehicleMapper) {
        this(vehicleMapper, null);
    }

    public MaintenanceResponse toResponse(MaintenanceRecord record) {
        if (record == null) {
            return null;
        }
        boolean hasAttachment = attachmentRepository != null && record.getId() != null
                && attachmentRepository.existsByMaintenanceId(record.getId());
        return toResponse(record, hasAttachment);
    }

    public MaintenanceResponse toResponse(MaintenanceRecord record, boolean hasAttachment) {
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
                record.getUpdatedAt(),
                hasAttachment
        );
    }

    public MaintenanceDetailResponse toDetailResponse(MaintenanceRecord record) {
        if (record == null) {
            return null;
        }
        boolean hasAttachment = attachmentRepository != null && record.getId() != null
                && attachmentRepository.existsByMaintenanceId(record.getId());
        return toDetailResponse(record, hasAttachment);
    }

    public MaintenanceDetailResponse toDetailResponse(MaintenanceRecord record, boolean hasAttachment) {
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
                vehicleSummary,
                hasAttachment
        );
    }
}
