package com.ilmiogarage.service;

import com.ilmiogarage.dto.vehicle.CreateVehicleRequest;
import com.ilmiogarage.dto.vehicle.UpdateVehicleRequest;
import com.ilmiogarage.dto.vehicle.VehicleResponse;
import com.ilmiogarage.exception.PlateAlreadyExistsException;
import com.ilmiogarage.exception.ResourceNotFoundException;
import com.ilmiogarage.exception.ValidationException;
import com.ilmiogarage.mapper.VehicleMapper;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.model.entity.Vehicle;
import com.ilmiogarage.model.enums.VehicleIcon;
import com.ilmiogarage.repository.MaintenanceRecordRepository;
import com.ilmiogarage.repository.UserRepository;
import com.ilmiogarage.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.Year;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private MaintenanceRecordRepository maintenanceRecordRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VehicleMapper vehicleMapper;

    @InjectMocks
    private VehicleService vehicleService;

    private User owner;
    private UUID ownerId;
    private Vehicle vehicle;
    private UUID vehicleId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        owner = new User(ownerId, "Mario Rossi", "mario@example.com", "hash123");

        vehicleId = UUID.randomUUID();
        vehicle = new Vehicle(owner, VehicleIcon.carFront, "Fiat", "Panda", 2020, "AB123CD", 50000);
        vehicle.setId(vehicleId);
        vehicle.setCreatedAt(Instant.now());
        vehicle.setUpdatedAt(Instant.now());
    }

    @Test
    @DisplayName("Creazione veicolo con successo")
    void createVehicle_success() {
        CreateVehicleRequest request = new CreateVehicleRequest(
                VehicleIcon.carFront, "Fiat", "Panda", 2020, "ab123cd", 50000);

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(vehicleRepository.existsByOwnerIdAndPlateIgnoreCase(ownerId, "AB123CD")).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(vehicle);
        when(vehicleMapper.toResponse(any(Vehicle.class), any())).thenReturn(
                new VehicleResponse(vehicleId, VehicleIcon.carFront, "Fiat", "Panda", 2020, "AB123CD", 50000,
                        Instant.now(), Instant.now(), null));

        VehicleResponse response = vehicleService.createVehicle(ownerId, request);

        assertThat(response).isNotNull();
        assertThat(response.plate()).isEqualTo("AB123CD");
        verify(vehicleRepository).save(any(Vehicle.class));
    }

    @Test
    @DisplayName("Creazione veicolo con targa duplicata per lo stesso utente fallisce con PlateAlreadyExistsException")
    void createVehicle_duplicatePlate_throwsException() {
        CreateVehicleRequest request = new CreateVehicleRequest(
                VehicleIcon.carFront, "Fiat", "Panda", 2020, "AB123CD", 50000);

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(vehicleRepository.existsByOwnerIdAndPlateIgnoreCase(ownerId, "AB123CD")).thenReturn(true);

        assertThatThrownBy(() -> vehicleService.createVehicle(ownerId, request))
                .isInstanceOf(PlateAlreadyExistsException.class)
                .hasMessageContaining("AB123CD");

        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Creazione veicolo con anno non valido fallisce")
    void createVehicle_invalidYear_throwsValidationException() {
        CreateVehicleRequest request = new CreateVehicleRequest(
                VehicleIcon.carFront, "Fiat", "Panda", 1899, "AB123CD", 50000);

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> vehicleService.createVehicle(ownerId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("1900");

        int futureYear = Year.now().getValue() + 5;
        CreateVehicleRequest futureRequest = new CreateVehicleRequest(
                VehicleIcon.carFront, "Fiat", "Panda", futureYear, "AB123CD", 50000);

        assertThatThrownBy(() -> vehicleService.createVehicle(ownerId, futureRequest))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Aggiornamento esplicito chilometraggio con valore negativo fallisce")
    void updateVehicle_negativeMileage_throwsValidationException() {
        UpdateVehicleRequest request = new UpdateVehicleRequest(null, null, null, null, null, -100);
        when(vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> vehicleService.updateVehicle(vehicleId, ownerId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maggiore o uguale a zero");
    }

    @Test
    @DisplayName("Cancellazione veicolo non trovato solleva ResourceNotFoundException")
    void deleteVehicle_notFound_throwsException() {
        when(vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.deleteVehicle(vehicleId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(vehicleRepository, never()).delete(any());
    }
}
