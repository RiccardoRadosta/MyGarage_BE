package com.ilmiogarage.validation;

import com.ilmiogarage.dto.auth.RegisterRequest;
import com.ilmiogarage.dto.maintenance.CreateMaintenanceRequest;
import com.ilmiogarage.dto.vehicle.CreateVehicleRequest;
import com.ilmiogarage.model.enums.MaintenanceType;
import com.ilmiogarage.model.enums.VehicleIcon;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationAndEnumsTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("VehicleIcon accetta solo i valori del frontend")
    void vehicleIcon_frontendValues() {
        assertThat(VehicleIcon.fromValue("carFront")).isEqualTo(VehicleIcon.carFront);
        assertThat(VehicleIcon.fromValue("car")).isEqualTo(VehicleIcon.car);
        assertThat(VehicleIcon.fromValue("motorbike")).isEqualTo(VehicleIcon.motorbike);
        assertThat(VehicleIcon.fromValue("scooter")).isEqualTo(VehicleIcon.scooter);
        assertThat(VehicleIcon.fromValue("truck")).isEqualTo(VehicleIcon.truck);
        assertThat(VehicleIcon.fromValue("van")).isEqualTo(VehicleIcon.van);

        assertThatThrownBy(() -> VehicleIcon.fromValue("bicycle"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Icona veicolo non valida");
    }

    @Test
    @DisplayName("MaintenanceType restituisce etichette corrette e gestisce OTHER")
    void maintenanceType_labels() {
        assertThat(MaintenanceType.OIL_CHANGE.getDefaultLabel()).isEqualTo("Cambio olio");
        assertThat(MaintenanceType.SERVICE.getDefaultLabel()).isEqualTo("Tagliando");
        assertThat(MaintenanceType.BRAKES.getDefaultLabel()).isEqualTo("Freni / pastiglie");
        assertThat(MaintenanceType.TIRE_CHANGE.getDefaultLabel()).isEqualTo("Cambio gomme");
        assertThat(MaintenanceType.WIPER_CHANGE.getDefaultLabel()).isEqualTo("Cambio tergicristalli");
        assertThat(MaintenanceType.INSPECTION.getDefaultLabel()).isEqualTo("Revisione");
        assertThat(MaintenanceType.BATTERY.getDefaultLabel()).isEqualTo("Batteria");
        assertThat(MaintenanceType.TIMING_BELT.getDefaultLabel()).isEqualTo("Distribuzione");
        assertThat(MaintenanceType.OTHER.getDefaultLabel()).isEqualTo("Altro");

        assertThat(MaintenanceType.OTHER.resolveLabel("Sostituzione candele")).isEqualTo("Sostituzione candele");
        assertThat(MaintenanceType.OIL_CHANGE.resolveLabel("Qualsiasi")).isEqualTo("Cambio olio");
    }

    @Test
    @DisplayName("Password minore di 8 caratteri viene rifiutata da Bean Validation")
    void registerRequest_passwordTooShort_failsValidation() {
        RegisterRequest request = new RegisterRequest("Mario", "mario@example.com", "pass12");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("almeno 8 caratteri");
    }

    @Test
    @DisplayName("Chilometraggio negativo veicolo viene rifiutato")
    void createVehicleRequest_negativeMileage_failsValidation() {
        CreateVehicleRequest request = new CreateVehicleRequest(
                VehicleIcon.carFront, "Alfa Romeo", "Giulia", 2021, "AB123CD", -5);
        Set<ConstraintViolation<CreateVehicleRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("mileage"));
    }

    @Test
    @DisplayName("Costo negativo o più di due decimali viene rifiutato")
    void createMaintenanceRequest_invalidCost_failsValidation() {
        CreateMaintenanceRequest negativeCost = new CreateMaintenanceRequest(
                MaintenanceType.OIL_CHANGE, null, LocalDate.now(), 10000, new BigDecimal("-10.00"), null);
        Set<ConstraintViolation<CreateMaintenanceRequest>> violations = validator.validate(negativeCost);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("cost"));

        CreateMaintenanceRequest threeDecimals = new CreateMaintenanceRequest(
                MaintenanceType.OIL_CHANGE, null, LocalDate.now(), 10000, new BigDecimal("10.555"), null);
        Set<ConstraintViolation<CreateMaintenanceRequest>> violations2 = validator.validate(threeDecimals);
        assertThat(violations2).anyMatch(v -> v.getPropertyPath().toString().equals("cost"));
    }
}
