package com.ilmiogarage.service;

import com.ilmiogarage.dto.vehicle.CreateVehicleRequest;
import com.ilmiogarage.dto.vehicle.UpdateVehicleRequest;
import com.ilmiogarage.dto.vehicle.VehicleResponse;
import com.ilmiogarage.exception.PlateAlreadyExistsException;
import com.ilmiogarage.exception.ResourceNotFoundException;
import com.ilmiogarage.exception.UpgradeRequiredException;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
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

    @Mock
    private SubscriptionService subscriptionService;

    @Spy
    private Clock clock = Clock.systemUTC();

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
        vehicle = new Vehicle(owner, VehicleIcon.carFront, "Fiat", "Panda", 2020, "AB123CD", 50000, true);
        vehicle.setId(vehicleId);
        vehicle.setCreatedAt(Instant.now());
        vehicle.setUpdatedAt(Instant.now());
    }

    @Test
    @DisplayName("Creazione primo veicolo con successo: diventa automaticamente principale")
    void createVehicle_success_firstVehicleBecomesPrimary() {
        CreateVehicleRequest request = new CreateVehicleRequest(
                VehicleIcon.carFront, "Fiat", "Panda", 2020, "ab123cd", 50000);

        when(userRepository.findByIdForUpdate(ownerId)).thenReturn(Optional.of(owner));
        when(vehicleRepository.countByOwnerId(ownerId)).thenReturn(0);
        when(subscriptionService.isPremium(ownerId)).thenReturn(false);
        when(vehicleRepository.existsByOwnerIdAndPlateIgnoreCase(ownerId, "AB123CD")).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> {
            Vehicle v = i.getArgument(0);
            assertThat(v.isPrimary()).isTrue();
            return v;
        });
        when(vehicleMapper.toResponse(any(Vehicle.class), any())).thenReturn(
                new VehicleResponse(vehicleId, VehicleIcon.carFront, "Fiat", "Panda", 2020, "AB123CD", 50000,
                        true, Instant.now(), Instant.now(), null));

        VehicleResponse response = vehicleService.createVehicle(ownerId, request);

        assertThat(response).isNotNull();
        assertThat(response.isPrimary()).isTrue();
        verify(vehicleRepository).save(any(Vehicle.class));
    }

    @Test
    @DisplayName("Utente Free che tenta di creare il secondo veicolo riceve UpgradeRequiredException")
    void createVehicle_freeUserSecondVehicle_throwsUpgradeRequired() {
        CreateVehicleRequest request = new CreateVehicleRequest(
                VehicleIcon.carFront, "Fiat", "Panda", 2020, "AB123CD", 50000);

        when(userRepository.findByIdForUpdate(ownerId)).thenReturn(Optional.of(owner));
        when(vehicleRepository.countByOwnerId(ownerId)).thenReturn(1);
        when(subscriptionService.isPremium(ownerId)).thenReturn(false);

        assertThatThrownBy(() -> vehicleService.createVehicle(ownerId, request))
                .isInstanceOf(UpgradeRequiredException.class)
                .hasMessageContaining("massimo un veicolo");

        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Utente Premium può creare più veicoli (il secondo non è principale)")
    void createVehicle_premiumUserMultipleVehicles_success() {
        CreateVehicleRequest request = new CreateVehicleRequest(
                VehicleIcon.motorbike, "Yamaha", "MT-07", 2022, "CD456EF", 15000);

        when(userRepository.findByIdForUpdate(ownerId)).thenReturn(Optional.of(owner));
        when(vehicleRepository.countByOwnerId(ownerId)).thenReturn(1);
        when(subscriptionService.isPremium(ownerId)).thenReturn(true);
        when(vehicleRepository.existsByOwnerIdAndPlateIgnoreCase(ownerId, "CD456EF")).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> {
            Vehicle v = i.getArgument(0);
            assertThat(v.isPrimary()).isFalse();
            return v;
        });
        when(vehicleMapper.toResponse(any(Vehicle.class), any())).thenReturn(
                new VehicleResponse(UUID.randomUUID(), VehicleIcon.motorbike, "Yamaha", "MT-07", 2022, "CD456EF", 15000,
                        false, Instant.now(), Instant.now(), null));

        VehicleResponse response = vehicleService.createVehicle(ownerId, request);

        assertThat(response).isNotNull();
        assertThat(response.isPrimary()).isFalse();
        verify(vehicleRepository).save(any(Vehicle.class));
    }

    @Test
    @DisplayName("Creazione veicolo con targa duplicata per lo stesso utente fallisce con PlateAlreadyExistsException")
    void createVehicle_duplicatePlate_throwsException() {
        CreateVehicleRequest request = new CreateVehicleRequest(
                VehicleIcon.carFront, "Fiat", "Panda", 2020, "AB123CD", 50000);

        when(userRepository.findByIdForUpdate(ownerId)).thenReturn(Optional.of(owner));
        when(vehicleRepository.countByOwnerId(ownerId)).thenReturn(0);
        when(subscriptionService.isPremium(ownerId)).thenReturn(false);
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

        when(userRepository.findByIdForUpdate(ownerId)).thenReturn(Optional.of(owner));
        when(vehicleRepository.countByOwnerId(ownerId)).thenReturn(0);
        when(subscriptionService.isPremium(ownerId)).thenReturn(false);

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
    @DisplayName("Utente Free può modificare il proprio veicolo principale")
    void updateVehicle_freeUserPrimaryVehicle_success() {
        UpdateVehicleRequest request = new UpdateVehicleRequest(VehicleIcon.car, "Fiat", "Panda", 2021, "AB123CD", 55000);
        vehicle.setPrimary(true);

        when(vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)).thenReturn(Optional.of(vehicle));
        when(subscriptionService.isPremium(ownerId)).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(vehicle);
        when(vehicleMapper.toResponse(any(Vehicle.class), any())).thenReturn(
                new VehicleResponse(vehicleId, VehicleIcon.car, "Fiat", "Panda", 2021, "AB123CD", 55000,
                        true, Instant.now(), Instant.now(), null));

        VehicleResponse response = vehicleService.updateVehicle(vehicleId, ownerId, request);

        assertThat(response).isNotNull();
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    @DisplayName("Utente Free che tenta di modificare un veicolo secondario riceve UpgradeRequiredException")
    void updateVehicle_freeUserSecondaryVehicle_throwsUpgradeRequired() {
        UpdateVehicleRequest request = new UpdateVehicleRequest(VehicleIcon.car, "Fiat", "Panda", 2021, "AB123CD", 55000);
        vehicle.setPrimary(false);

        when(vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)).thenReturn(Optional.of(vehicle));
        when(subscriptionService.isPremium(ownerId)).thenReturn(false);

        assertThatThrownBy(() -> vehicleService.updateVehicle(vehicleId, ownerId, request))
                .isInstanceOf(UpgradeRequiredException.class)
                .hasMessageContaining("veicolo principale");

        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aggiornamento esplicito chilometraggio con valore negativo fallisce")
    void updateVehicle_negativeMileage_throwsValidationException() {
        UpdateVehicleRequest request = new UpdateVehicleRequest(null, null, null, null, null, -100);
        when(vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)).thenReturn(Optional.of(vehicle));
        when(subscriptionService.isPremium(ownerId)).thenReturn(true);

        assertThatThrownBy(() -> vehicleService.updateVehicle(vehicleId, ownerId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maggiore o uguale a zero");
    }

    @Test
    @DisplayName("Utente Free che tenta di eliminare un veicolo riceve UpgradeRequiredException")
    void deleteVehicle_freeUser_throwsUpgradeRequired() {
        when(vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)).thenReturn(Optional.of(vehicle));
        when(subscriptionService.isPremium(ownerId)).thenReturn(false);

        assertThatThrownBy(() -> vehicleService.deleteVehicle(vehicleId, ownerId))
                .isInstanceOf(UpgradeRequiredException.class)
                .hasMessageContaining("riservata agli utenti Premium");

        verify(vehicleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Utente Premium può eliminare un veicolo")
    void deleteVehicle_premiumUser_success() {
        when(vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)).thenReturn(Optional.of(vehicle));
        when(subscriptionService.isPremium(ownerId)).thenReturn(true);

        vehicleService.deleteVehicle(vehicleId, ownerId);

        verify(vehicleRepository).delete(vehicle);
    }

    @Test
    @DisplayName("Cancellazione veicolo non trovato solleva ResourceNotFoundException")
    void deleteVehicle_notFound_throwsException() {
        when(vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.deleteVehicle(vehicleId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(vehicleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Utente Free che tenta di impostare veicolo principale riceve UpgradeRequiredException")
    void setPrimaryVehicle_freeUser_throwsUpgradeRequired() {
        when(userRepository.findByIdForUpdate(ownerId)).thenReturn(Optional.of(owner));
        when(vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)).thenReturn(Optional.of(vehicle));
        when(subscriptionService.isPremium(ownerId)).thenReturn(false);
        when(subscriptionService.isPrimaryVehicleSelectionRequired(ownerId)).thenReturn(false);

        assertThatThrownBy(() -> vehicleService.setPrimaryVehicle(vehicleId, ownerId))
                .isInstanceOf(UpgradeRequiredException.class)
                .hasMessageContaining("riservata agli utenti Premium");

        verify(vehicleRepository, never()).clearPrimaryForOwner(any());
    }

    @Test
    @DisplayName("Utente Premium può impostare un veicolo come principale")
    void setPrimaryVehicle_premiumUser_success() {
        vehicle.setPrimary(false);
        when(userRepository.findByIdForUpdate(ownerId)).thenReturn(Optional.of(owner));
        when(vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)).thenReturn(Optional.of(vehicle));
        when(subscriptionService.isPremium(ownerId)).thenReturn(true);
        when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenReturn(vehicle);
        when(vehicleMapper.toResponse(any(Vehicle.class), any())).thenReturn(
                new VehicleResponse(vehicleId, VehicleIcon.carFront, "Fiat", "Panda", 2020, "AB123CD", 50000,
                        true, Instant.now(), Instant.now(), null));

        VehicleResponse response = vehicleService.setPrimaryVehicle(vehicleId, ownerId);

        assertThat(response).isNotNull();
        verify(vehicleRepository).clearPrimaryForOwner(ownerId);
        verify(vehicleRepository).saveAndFlush(vehicle);
    }

    @Test
    @DisplayName("Utente Free in stato di scelta obbligatoria può impostare il veicolo principale")
    void setPrimaryVehicle_freeUser_whenSelectionRequired_success() {
        vehicle.setPrimary(false);
        when(userRepository.findByIdForUpdate(ownerId)).thenReturn(Optional.of(owner));
        when(vehicleRepository.findByIdAndOwnerId(vehicleId, ownerId)).thenReturn(Optional.of(vehicle));
        when(subscriptionService.isPremium(ownerId)).thenReturn(false);
        when(subscriptionService.isPrimaryVehicleSelectionRequired(ownerId)).thenReturn(true);
        when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenReturn(vehicle);
        when(vehicleMapper.toResponse(any(Vehicle.class), any())).thenReturn(
                new VehicleResponse(vehicleId, VehicleIcon.carFront, "Fiat", "Panda", 2020, "AB123CD", 50000,
                        true, Instant.now(), Instant.now(), null));

        VehicleResponse response = vehicleService.setPrimaryVehicle(vehicleId, ownerId);

        assertThat(response).isNotNull();
        verify(vehicleRepository).clearPrimaryForOwner(ownerId);
        verify(vehicleRepository).saveAndFlush(vehicle);
        verify(userRepository).save(owner);
        assertThat(owner.getPrimaryVehicleSelectedAt()).isNotNull();
    }
}
