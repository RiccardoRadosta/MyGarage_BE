package com.ilmiogarage.service;

import com.ilmiogarage.dto.maintenance.CreateMaintenanceRequest;
import com.ilmiogarage.dto.maintenance.MaintenanceResponse;
import com.ilmiogarage.dto.maintenance.UpdateMaintenanceRequest;
import com.ilmiogarage.exception.InvalidDateException;
import com.ilmiogarage.exception.ResourceNotFoundException;
import com.ilmiogarage.exception.ValidationException;
import com.ilmiogarage.mapper.MaintenanceMapper;
import com.ilmiogarage.model.entity.MaintenanceRecord;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.model.entity.Vehicle;
import com.ilmiogarage.model.enums.MaintenanceType;
import com.ilmiogarage.model.enums.VehicleIcon;
import com.ilmiogarage.repository.MaintenanceRecordRepository;
import com.ilmiogarage.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    @Mock
    private MaintenanceRecordRepository maintenanceRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private MaintenanceMapper maintenanceMapper;

    @InjectMocks
    private MaintenanceService maintenanceService;

    private User owner;
    private UUID ownerId;
    private Vehicle vehicle;
    private UUID vehicleId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        owner = new User(ownerId, "Luigi Verdi", "luigi@example.com", "hash");

        vehicleId = UUID.randomUUID();
        vehicle = new Vehicle(owner, VehicleIcon.car, "Volkswagen", "Golf", 2021, "CD456EF", 40000);
        vehicle.setId(vehicleId);
    }

    @Test
    @DisplayName("Regola km: nuova manutenzione con km superiore aggiorna il veicolo")
    void createMaintenance_higherMileage_updatesVehicle() {
        CreateMaintenanceRequest request = new CreateMaintenanceRequest(
                MaintenanceType.OIL_CHANGE,
                null,
                LocalDate.now().minusDays(2),
                45000, // higher than vehicle's 40000
                new BigDecimal("120.00"),
                "Cambio olio sintetico"
        );

        when(vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)).thenReturn(Optional.of(vehicle));
        when(maintenanceRepository.save(any(MaintenanceRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(maintenanceMapper.toResponse(any())).thenReturn(
                new MaintenanceResponse(UUID.randomUUID(), vehicleId, MaintenanceType.OIL_CHANGE, "Cambio olio",
                        null, LocalDate.now().minusDays(2), 45000, new BigDecimal("120.00"), "note",
                        Instant.now(), Instant.now()));

        maintenanceService.createMaintenance(vehicleId, ownerId, request);

        assertThat(vehicle.getMileage()).isEqualTo(45000);
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    @DisplayName("Regola km: manutenzione storica con km inferiore non riduce il chilometraggio del veicolo")
    void createMaintenance_lowerMileage_doesNotDecreaseVehicle() {
        CreateMaintenanceRequest request = new CreateMaintenanceRequest(
                MaintenanceType.OIL_CHANGE,
                null,
                LocalDate.now().minusDays(30),
                35000, // lower than vehicle's 40000
                new BigDecimal("120.00"),
                "Intervento passato"
        );

        when(vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)).thenReturn(Optional.of(vehicle));
        when(maintenanceRepository.save(any(MaintenanceRecord.class))).thenAnswer(i -> i.getArgument(0));

        maintenanceService.createMaintenance(vehicleId, ownerId, request);

        assertThat(vehicle.getMileage()).isEqualTo(40000);
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Regola OTHER: tipo OTHER senza customDescription fallisce")
    void createMaintenance_otherWithoutDescription_throwsValidationException() {
        CreateMaintenanceRequest request = new CreateMaintenanceRequest(
                MaintenanceType.OTHER,
                null, // missing
                LocalDate.now().minusDays(1),
                40000,
                new BigDecimal("50.00"),
                null
        );

        when(vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> maintenanceService.createMaintenance(vehicleId, ownerId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("OTHER");

        verify(maintenanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Regola non-OTHER: tipo standard con customDescription non null fallisce")
    void createMaintenance_standardTypeWithDescription_throwsValidationException() {
        CreateMaintenanceRequest request = new CreateMaintenanceRequest(
                MaintenanceType.OIL_CHANGE,
                "Descrizione non permessa",
                LocalDate.now().minusDays(1),
                40000,
                new BigDecimal("50.00"),
                null
        );

        when(vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> maintenanceService.createMaintenance(vehicleId, ownerId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("Regola date: data nel futuro fallisce con InvalidDateException")
    void createMaintenance_futureDate_throwsInvalidDateException() {
        CreateMaintenanceRequest request = new CreateMaintenanceRequest(
                MaintenanceType.BRAKES,
                null,
                LocalDate.now().plusDays(2), // future
                40000,
                new BigDecimal("200.00"),
                null
        );

        when(vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> maintenanceService.createMaintenance(vehicleId, ownerId, request))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("futura");
    }

    @Test
    @DisplayName("Regola km: modifica manutenzione con km superiore aggiorna il veicolo")
    void updateMaintenance_higherMileage_updatesVehicle() {
        UUID maintenanceId = UUID.randomUUID();
        MaintenanceRecord record = new MaintenanceRecord(owner, vehicle, MaintenanceType.SERVICE, null,
                LocalDate.now().minusDays(5), 38000, new BigDecimal("250.00"), "Tagliando");
        record.setId(maintenanceId);

        when(maintenanceRepository.findByIdAndOwnerId(maintenanceId, ownerId)).thenReturn(Optional.of(record));
        when(maintenanceRepository.save(any())).thenReturn(record);

        UpdateMaintenanceRequest updateRequest = new UpdateMaintenanceRequest(
                null, null, null, 48000, null, null // 48000 > 40000
        );

        maintenanceService.updateMaintenance(maintenanceId, ownerId, updateRequest);

        assertThat(vehicle.getMileage()).isEqualTo(48000);
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    @DisplayName("Regola km: eliminazione manutenzione NON riduce il chilometraggio del veicolo")
    void deleteMaintenance_doesNotDecreaseVehicleMileage() {
        UUID maintenanceId = UUID.randomUUID();
        MaintenanceRecord record = new MaintenanceRecord(owner, vehicle, MaintenanceType.SERVICE, null,
                LocalDate.now().minusDays(5), 40000, new BigDecimal("250.00"), "Tagliando");
        record.setId(maintenanceId);

        when(maintenanceRepository.findByIdAndOwnerId(maintenanceId, ownerId)).thenReturn(Optional.of(record));

        maintenanceService.deleteMaintenance(maintenanceId, ownerId);

        assertThat(vehicle.getMileage()).isEqualTo(40000);
        verify(maintenanceRepository).delete(record);
        verify(vehicleRepository, never()).save(any());
    }
}
