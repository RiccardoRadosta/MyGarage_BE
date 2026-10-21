package com.ilmiogarage.service;

import com.ilmiogarage.dto.vehicle.CreateVehicleRequest;
import com.ilmiogarage.dto.vehicle.LastMaintenanceSummary;
import com.ilmiogarage.dto.vehicle.UpdateVehicleRequest;
import com.ilmiogarage.dto.vehicle.VehicleDetailResponse;
import com.ilmiogarage.dto.vehicle.VehicleResponse;
import com.ilmiogarage.exception.PlateAlreadyExistsException;
import com.ilmiogarage.exception.ResourceNotFoundException;
import com.ilmiogarage.exception.ValidationException;
import com.ilmiogarage.mapper.VehicleMapper;
import com.ilmiogarage.model.entity.MaintenanceRecord;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.model.entity.Vehicle;
import com.ilmiogarage.repository.MaintenanceRecordRepository;
import com.ilmiogarage.repository.UserRepository;
import com.ilmiogarage.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class VehicleService {

    private static final Logger log = LoggerFactory.getLogger(VehicleService.class);

    private final VehicleRepository vehicleRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final UserRepository userRepository;
    private final VehicleMapper vehicleMapper;

    public VehicleService(
            VehicleRepository vehicleRepository,
            MaintenanceRecordRepository maintenanceRecordRepository,
            UserRepository userRepository,
            VehicleMapper vehicleMapper) {
        this.vehicleRepository = vehicleRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.userRepository = userRepository;
        this.vehicleMapper = vehicleMapper;
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> getAllVehicles(UUID ownerId) {
        List<Vehicle> vehicles = vehicleRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId);
        return vehicles.stream()
                .map(vehicle -> {
                    LastMaintenanceSummary lastMaintenance = findLastMaintenanceSummary(vehicle.getId(), ownerId);
                    return vehicleMapper.toResponse(vehicle, lastMaintenance);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleDetailResponse getVehicleById(UUID vehicleId, UUID ownerId) {
        Vehicle vehicle = vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Veicolo non trovato."));

        LastMaintenanceSummary lastMaintenance = findLastMaintenanceSummary(vehicleId, ownerId);
        long count = maintenanceRecordRepository.countByVehicleIdAndOwnerId(vehicleId, ownerId);

        return vehicleMapper.toDetailResponse(vehicle, lastMaintenance, count);
    }

    @Transactional
    public VehicleResponse createVehicle(UUID ownerId, CreateVehicleRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato."));

        String normalizedPlate = request.plate().trim().toUpperCase();

        if (vehicleRepository.existsByOwnerIdAndPlateIgnoreCase(ownerId, normalizedPlate)) {
            log.warn("Vehicle creation rejected: plate {} already exists for user {}", normalizedPlate, ownerId);
            throw new PlateAlreadyExistsException("Un veicolo con targa '" + normalizedPlate + "' è già presente nel tuo garage.");
        }

        validateYear(request.year());

        Vehicle vehicle = new Vehicle(
                owner,
                request.icon(),
                request.brand().trim(),
                request.model().trim(),
                request.year(),
                normalizedPlate,
                request.mileage()
        );

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Created vehicle {} for user {}", saved.getId(), ownerId);
        return vehicleMapper.toResponse(saved, null);
    }

    @Transactional
    public VehicleResponse updateVehicle(UUID vehicleId, UUID ownerId, UpdateVehicleRequest request) {
        Vehicle vehicle = vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Veicolo non trovato."));

        if (request.plate() != null && !request.plate().isBlank()) {
            String normalizedPlate = request.plate().trim().toUpperCase();
            if (!normalizedPlate.equalsIgnoreCase(vehicle.getPlate())) {
                if (vehicleRepository.existsByOwnerIdAndPlateIgnoreCaseAndIdNot(ownerId, normalizedPlate, vehicleId)) {
                    log.warn("Vehicle update rejected: plate {} already in use by another vehicle of user {}", normalizedPlate, ownerId);
                    throw new PlateAlreadyExistsException("Un veicolo con targa '" + normalizedPlate + "' è già presente nel tuo garage.");
                }
                vehicle.setPlate(normalizedPlate);
            }
        }

        if (request.icon() != null) {
            vehicle.setIcon(request.icon());
        }

        if (request.brand() != null && !request.brand().isBlank()) {
            vehicle.setBrand(request.brand().trim());
        }

        if (request.model() != null && !request.model().isBlank()) {
            vehicle.setModel(request.model().trim());
        }

        if (request.year() != null) {
            validateYear(request.year());
            vehicle.setYear(request.year());
        }

        if (request.mileage() != null) {
            if (request.mileage() < 0) {
                throw new ValidationException("mileage", "Min", "Il chilometraggio deve essere maggiore o uguale a zero.");
            }
            vehicle.setMileage(request.mileage());
        }

        Vehicle updated = vehicleRepository.save(vehicle);
        LastMaintenanceSummary lastMaintenance = findLastMaintenanceSummary(vehicleId, ownerId);
        log.info("Updated vehicle {} for user {}", vehicleId, ownerId);
        return vehicleMapper.toResponse(updated, lastMaintenance);
    }

    @Transactional
    public void deleteVehicle(UUID vehicleId, UUID ownerId) {
        Vehicle vehicle = vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Veicolo non trovato."));

        vehicleRepository.delete(vehicle);
        log.info("Deleted vehicle {} and all associated maintenance records for user {}", vehicleId, ownerId);
    }

    private LastMaintenanceSummary findLastMaintenanceSummary(UUID vehicleId, UUID ownerId) {
        Optional<MaintenanceRecord> lastOpt = maintenanceRecordRepository
                .findFirstByVehicleIdAndOwnerIdOrderByDateDescCreatedAtDesc(vehicleId, ownerId);
        return lastOpt.map(vehicleMapper::toLastMaintenanceSummary).orElse(null);
    }

    private void validateYear(int year) {
        int maxAllowedYear = Year.now().getValue() + 1;
        if (year < 1900 || year > maxAllowedYear) {
            throw new ValidationException("year", "Range",
                    "L'anno deve essere compreso tra il 1900 e il " + maxAllowedYear + ".");
        }
    }
}
