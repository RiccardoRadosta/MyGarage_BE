package com.ilmiogarage.service;

import com.ilmiogarage.dto.dashboard.DashboardResponse;
import com.ilmiogarage.dto.maintenance.MaintenanceResponse;
import com.ilmiogarage.dto.vehicle.LastMaintenanceSummary;
import com.ilmiogarage.dto.vehicle.VehicleResponse;
import com.ilmiogarage.mapper.MaintenanceMapper;
import com.ilmiogarage.mapper.VehicleMapper;
import com.ilmiogarage.model.entity.MaintenanceRecord;
import com.ilmiogarage.model.entity.Vehicle;
import com.ilmiogarage.repository.MaintenanceRecordRepository;
import com.ilmiogarage.repository.VehicleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DashboardService {

    private final VehicleRepository vehicleRepository;
    private final MaintenanceRecordRepository maintenanceRepository;
    private final VehicleMapper vehicleMapper;
    private final MaintenanceMapper maintenanceMapper;

    public DashboardService(
            VehicleRepository vehicleRepository,
            MaintenanceRecordRepository maintenanceRepository,
            VehicleMapper vehicleMapper,
            MaintenanceMapper maintenanceMapper) {
        this.vehicleRepository = vehicleRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.vehicleMapper = vehicleMapper;
        this.maintenanceMapper = maintenanceMapper;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(UUID ownerId) {
        int vehicleCount = vehicleRepository.countByOwnerId(ownerId);
        long maintenanceCount = maintenanceRepository.countByOwnerId(ownerId);
        BigDecimal totalSpent = maintenanceRepository.sumCostByOwnerId(ownerId);
        if (totalSpent == null) {
            totalSpent = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            totalSpent = totalSpent.setScale(2, RoundingMode.HALF_UP);
        }

        List<Vehicle> vehicles = vehicleRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId);
        List<VehicleResponse> vehicleResponses = vehicles.stream()
                .map(v -> {
                    Optional<MaintenanceRecord> lastOpt = maintenanceRepository
                            .findFirstByVehicleIdAndOwnerIdOrderByDateDescCreatedAtDesc(v.getId(), ownerId);
                    LastMaintenanceSummary lastSummary = lastOpt.map(vehicleMapper::toLastMaintenanceSummary).orElse(null);
                    return vehicleMapper.toResponse(v, lastSummary);
                })
                .toList();

        List<MaintenanceRecord> recentRecords = maintenanceRepository.findRecentByOwnerId(
                ownerId, PageRequest.of(0, 10));
        List<MaintenanceResponse> recentResponses = recentRecords.stream()
                .map(maintenanceMapper::toResponse)
                .toList();

        return new DashboardResponse(
                vehicleCount,
                maintenanceCount,
                totalSpent,
                vehicleResponses,
                recentResponses != null ? recentResponses : Collections.emptyList()
        );
    }
}
