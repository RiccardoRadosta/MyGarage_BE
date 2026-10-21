package com.ilmiogarage.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilmiogarage.dto.auth.RegisterRequest;
import com.ilmiogarage.dto.maintenance.CreateMaintenanceRequest;
import com.ilmiogarage.dto.maintenance.UpdateMaintenanceRequest;
import com.ilmiogarage.dto.vehicle.CreateVehicleRequest;
import com.ilmiogarage.model.enums.MaintenanceType;
import com.ilmiogarage.model.enums.VehicleIcon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MaintenanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private String vehicleId;

    @BeforeEach
    void setUp() throws Exception {
        // Registra utente
        MvcResult reg = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("Tester", "maint.tester@example.com", "Password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("accessToken").asText();

        // Crea veicolo con km iniziale 50000
        CreateVehicleRequest vReq = new CreateVehicleRequest(
                VehicleIcon.motorbike, "Ducati", "Monster", 2022, "AB000CD", 50000);
        MvcResult vRes = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vReq)))
                .andExpect(status().isCreated())
                .andReturn();
        vehicleId = objectMapper.readTree(vRes.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("Caso 3: Manutenzione nuova con km superiore aggiorna il veicolo")
    void newMaintenance_higherKm_updatesVehicleKm() throws Exception {
        CreateMaintenanceRequest mReq = new CreateMaintenanceRequest(
                MaintenanceType.SERVICE, null, LocalDate.now().minusDays(3), 55000, new BigDecimal("250.00"), "Tagliando 55k");

        mockMvc.perform(post("/api/v1/vehicles/" + vehicleId + "/maintenances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mileage").value(55000))
                .andExpect(jsonPath("$.typeLabel").value("Tagliando"));

        // Verifica che il veicolo sia stato aggiornato a 55000 km
        mockMvc.perform(get("/api/v1/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileage").value(55000))
                .andExpect(jsonPath("$.lastMaintenance.mileage").value(55000));
    }

    @Test
    @DisplayName("Caso 4: Manutenzione storica con km inferiore non riduce il km corrente")
    void historicalMaintenance_lowerKm_doesNotReduceVehicleKm() throws Exception {
        // Il veicolo ha 50000 km. Registriamo una manutenzione passata a 42000 km.
        CreateMaintenanceRequest mReq = new CreateMaintenanceRequest(
                MaintenanceType.OIL_CHANGE, null, LocalDate.now().minusMonths(6), 42000, new BigDecimal("80.00"), "Olio storico");

        mockMvc.perform(post("/api/v1/vehicles/" + vehicleId + "/maintenances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mileage").value(42000));

        // Il veicolo deve mantenere 50000 km
        mockMvc.perform(get("/api/v1/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileage").value(50000));
    }

    @Test
    @DisplayName("Caso 5: Modifica o cancellazione manutenzione non riduce il km corrente")
    void updateOrDeleteMaintenance_doesNotReduceCurrentMileage() throws Exception {
        // Registra manutenzione a 52000 km -> veicolo sale a 52000
        CreateMaintenanceRequest mReq = new CreateMaintenanceRequest(
                MaintenanceType.BRAKES, null, LocalDate.now().minusDays(2), 52000, new BigDecimal("140.00"), "Pastiglie");
        MvcResult mRes = mockMvc.perform(post("/api/v1/vehicles/" + vehicleId + "/maintenances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String mId = objectMapper.readTree(mRes.getResponse().getContentAsString()).get("id").asText();

        // 1. Modifica della manutenzione abbassando il km del record a 51000
        UpdateMaintenanceRequest patchReq = new UpdateMaintenanceRequest(null, null, null, 51000, null, null);
        mockMvc.perform(patch("/api/v1/maintenances/" + mId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileage").value(51000));

        // Il veicolo deve rimanere a 52000
        mockMvc.perform(get("/api/v1/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileage").value(52000));

        // 2. Cancellazione della manutenzione
        mockMvc.perform(delete("/api/v1/maintenances/" + mId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Il veicolo deve rimanere comunque a 52000 (rappresenta l'ultimo valore noto)
        mockMvc.perform(get("/api/v1/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileage").value(52000));
    }

    @Test
    @DisplayName("Caso 7: OTHER senza descrizione non è accettato (400 VALIDATION_ERROR)")
    void otherWithoutDescription_rejected() throws Exception {
        CreateMaintenanceRequest mReq = new CreateMaintenanceRequest(
                MaintenanceType.OTHER, null, LocalDate.now().minusDays(1), 50000, new BigDecimal("30.00"), null);

        mockMvc.perform(post("/api/v1/vehicles/" + vehicleId + "/maintenances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("customDescription"));

        // Se invece customDescription è fornita per OTHER, ha successo e typeLabel restituisce la descrizione
        CreateMaintenanceRequest validOther = new CreateMaintenanceRequest(
                MaintenanceType.OTHER, "Sostituzione specchietto", LocalDate.now().minusDays(1), 50000, new BigDecimal("45.00"), null);

        mockMvc.perform(post("/api/v1/vehicles/" + vehicleId + "/maintenances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validOther)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("OTHER"))
                .andExpect(jsonPath("$.typeLabel").value("Sostituzione specchietto"))
                .andExpect(jsonPath("$.customDescription").value("Sostituzione specchietto"));
    }

    @Test
    @DisplayName("Caso 8: Date impossibili e valori negativi non sono accettati")
    void invalidDateAndNegativeValues_rejected() throws Exception {
        // Data nel futuro
        CreateMaintenanceRequest futureDate = new CreateMaintenanceRequest(
                MaintenanceType.OIL_CHANGE, null, LocalDate.now().plusDays(5), 50000, new BigDecimal("50.00"), null);
        mockMvc.perform(post("/api/v1/vehicles/" + vehicleId + "/maintenances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(futureDate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE"));

        // Data anteriore al 1900
        CreateMaintenanceRequest oldDate = new CreateMaintenanceRequest(
                MaintenanceType.OIL_CHANGE, null, LocalDate.of(1880, 1, 1), 50000, new BigDecimal("50.00"), null);
        mockMvc.perform(post("/api/v1/vehicles/" + vehicleId + "/maintenances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldDate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE"));

        // Costo negativo
        CreateMaintenanceRequest negativeCost = new CreateMaintenanceRequest(
                MaintenanceType.OIL_CHANGE, null, LocalDate.now().minusDays(1), 50000, new BigDecimal("-15.00"), null);
        mockMvc.perform(post("/api/v1/vehicles/" + vehicleId + "/maintenances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeCost)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Chilometraggio negativo
        CreateMaintenanceRequest negativeKm = new CreateMaintenanceRequest(
                MaintenanceType.OIL_CHANGE, null, LocalDate.now().minusDays(1), -100, new BigDecimal("15.00"), null);
        mockMvc.perform(post("/api/v1/vehicles/" + vehicleId + "/maintenances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeKm)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
