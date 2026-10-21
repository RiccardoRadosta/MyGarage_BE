package com.ilmiogarage.service;

import com.ilmiogarage.dto.common.PagedResponse;
import com.ilmiogarage.dto.maintenance.CreateMaintenanceRequest;
import com.ilmiogarage.dto.maintenance.MaintenanceDetailResponse;
import com.ilmiogarage.dto.maintenance.MaintenanceResponse;
import com.ilmiogarage.dto.maintenance.UpdateMaintenanceRequest;
import com.ilmiogarage.exception.InvalidDateException;
import com.ilmiogarage.exception.ResourceNotFoundException;
import com.ilmiogarage.exception.ValidationException;
import com.ilmiogarage.mapper.MaintenanceMapper;
import com.ilmiogarage.model.entity.MaintenanceRecord;
import com.ilmiogarage.model.entity.Vehicle;
import com.ilmiogarage.model.enums.MaintenanceType;
import com.ilmiogarage.repository.MaintenanceRecordRepository;
import com.ilmiogarage.repository.VehicleRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

    private final MaintenanceRecordRepository maintenanceRepository;
    private final VehicleRepository vehicleRepository;
    private final MaintenanceMapper maintenanceMapper;

    public MaintenanceService(
            MaintenanceRecordRepository maintenanceRepository,
            VehicleRepository vehicleRepository,
            MaintenanceMapper maintenanceMapper) {
        this.maintenanceRepository = maintenanceRepository;
        this.vehicleRepository = vehicleRepository;
        this.maintenanceMapper = maintenanceMapper;
    }

    @Transactional
    public MaintenanceResponse createMaintenance(UUID vehicleId, UUID ownerId, CreateMaintenanceRequest request) {
        Vehicle vehicle = vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Veicolo non trovato."));

        validateTypeAndDescription(request.type(), request.customDescription());
        validateDate(request.date());

        if (request.mileage() < 0) {
            throw new ValidationException("mileage", "Min", "Il chilometraggio deve essere maggiore o uguale a zero.");
        }

        String cleanedCustomDesc = (request.type() == MaintenanceType.OTHER && request.customDescription() != null)
                ? request.customDescription().trim()
                : null;

        MaintenanceRecord record = new MaintenanceRecord(
                vehicle.getOwner(),
                vehicle,
                request.type(),
                cleanedCustomDesc,
                request.date(),
                request.mileage(),
                request.cost(),
                request.notes()
        );

        MaintenanceRecord saved = maintenanceRepository.save(record);

        // Automatic vehicle mileage sync rule
        if (request.mileage() > vehicle.getMileage()) {
            log.info("Updating vehicle {} mileage from {} to {} due to new maintenance {}",
                    vehicle.getId(), vehicle.getMileage(), request.mileage(), saved.getId());
            vehicle.setMileage(request.mileage());
            vehicleRepository.save(vehicle);
        }

        return maintenanceMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public MaintenanceDetailResponse getMaintenanceById(UUID maintenanceId, UUID ownerId) {
        MaintenanceRecord record = maintenanceRepository.findByIdAndOwnerId(maintenanceId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervento non trovato."));
        return maintenanceMapper.toDetailResponse(record);
    }

    @Transactional(readOnly = true)
    public PagedResponse<MaintenanceResponse> getVehicleMaintenances(
            UUID vehicleId, UUID ownerId, Pageable pageable) {

        if (!vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId).isPresent()) {
            throw new ResourceNotFoundException("Veicolo non trovato.");
        }

        Pageable safePageable = sanitizePageable(pageable);
        Page<MaintenanceRecord> page = maintenanceRepository.findByVehicleIdAndOwnerId(vehicleId, ownerId, safePageable);
        List<MaintenanceResponse> dtos = page.getContent().stream()
                .map(maintenanceMapper::toResponse)
                .toList();

        return PagedResponse.from(page, dtos);
    }

    @Transactional(readOnly = true)
    public PagedResponse<MaintenanceResponse> getMaintenancesFiltered(
            UUID ownerId, UUID vehicleId, MaintenanceType type, LocalDate from, LocalDate to, Pageable pageable) {

        Pageable safePageable = sanitizePageable(pageable);

        Specification<MaintenanceRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("owner").get("id"), ownerId));

            if (vehicleId != null) {
                predicates.add(cb.equal(root.get("vehicle").get("id"), vehicleId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("date"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("date"), to));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<MaintenanceRecord> page = maintenanceRepository.findAll(spec, safePageable);
        List<MaintenanceResponse> dtos = page.getContent().stream()
                .map(maintenanceMapper::toResponse)
                .toList();

        return PagedResponse.from(page, dtos);
    }

    @Transactional
    public MaintenanceResponse updateMaintenance(UUID maintenanceId, UUID ownerId, UpdateMaintenanceRequest request) {
        MaintenanceRecord record = maintenanceRepository.findByIdAndOwnerId(maintenanceId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervento non trovato."));

        Vehicle vehicle = record.getVehicle();

        MaintenanceType newType = request.type() != null ? request.type() : record.getType();
        String newCustomDesc = request.customDescription() != null ? request.customDescription() : record.getCustomDescription();

        if (request.type() != null || request.customDescription() != null) {
            validateTypeAndDescription(newType, newCustomDesc);
            record.setType(newType);
            record.setCustomDescription(newType == MaintenanceType.OTHER ? newCustomDesc.trim() : null);
        }

        if (request.date() != null) {
            validateDate(request.date());
            record.setDate(request.date());
        }

        if (request.mileage() != null) {
            if (request.mileage() < 0) {
                throw new ValidationException("mileage", "Min", "Il chilometraggio deve essere maggiore o uguale a zero.");
            }
            record.setMileage(request.mileage());

            // If updated mileage exceeds vehicle mileage, update vehicle
            if (request.mileage() > vehicle.getMileage()) {
                log.info("Updating vehicle {} mileage from {} to {} due to maintenance update {}",
                        vehicle.getId(), vehicle.getMileage(), request.mileage(), record.getId());
                vehicle.setMileage(request.mileage());
                vehicleRepository.save(vehicle);
            }
        }

        if (request.cost() != null) {
            if (request.cost().signum() < 0) {
                throw new ValidationException("cost", "DecimalMin", "Il costo non può essere negativo.");
            }
            record.setCost(request.cost().setScale(2, RoundingMode.HALF_UP));
        }

        if (request.notes() != null) {
            record.setNotes(request.notes());
        }

        MaintenanceRecord saved = maintenanceRepository.save(record);
        log.info("Updated maintenance {} for user {}", maintenanceId, ownerId);
        return maintenanceMapper.toResponse(saved);
    }

    @Transactional
    public void deleteMaintenance(UUID maintenanceId, UUID ownerId) {
        MaintenanceRecord record = maintenanceRepository.findByIdAndOwnerId(maintenanceId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervento non trovato."));

        maintenanceRepository.delete(record);
        log.info("Deleted maintenance {} for user {}. Vehicle mileage was preserved.", maintenanceId, ownerId);
    }

    private void validateTypeAndDescription(MaintenanceType type, String customDescription) {
        if (type == MaintenanceType.OTHER) {
            if (customDescription == null || customDescription.isBlank()) {
                throw new ValidationException("customDescription", "NotBlank",
                        "Per il tipo 'OTHER', la descrizione personalizzata è obbligatoria.");
            }
            if (customDescription.trim().length() > 100) {
                throw new ValidationException("customDescription", "Size",
                        "La descrizione personalizzata non può superare i 100 caratteri.");
            }
        } else {
            if (customDescription != null && !customDescription.isBlank()) {
                throw new ValidationException("customDescription", "Null",
                        "La descrizione personalizzata deve essere assente o nulla quando il tipo non è OTHER.");
            }
        }
    }

    private void validateDate(LocalDate date) {
        if (date == null) {
            return;
        }
        if (date.isAfter(LocalDate.now())) {
            throw new InvalidDateException("La data dell'intervento non può essere futura (" + date + ").");
        }
        if (date.isBefore(LocalDate.of(1900, 1, 1))) {
            throw new InvalidDateException("La data dell'intervento non può essere anteriore all'anno 1900.");
        }
    }

    private Pageable sanitizePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = pageable.getPageSize() > 0 ? Math.min(pageable.getPageSize(), 100) : 20;

        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, "date").and(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return PageRequest.of(page, size, sort);
    }
}
