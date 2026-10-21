package com.ilmiogarage.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilmiogarage.dto.auth.RegisterRequest;
import com.ilmiogarage.dto.maintenance.CreateMaintenanceRequest;
import com.ilmiogarage.dto.vehicle.CreateVehicleRequest;
import com.ilmiogarage.dto.vehicle.UpdateVehicleRequest;
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
class OwnershipAndSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String user1Token;
    private String user2Token;
    private String user1VehicleId;
    private String user1MaintenanceId;

    @BeforeEach
    void setUp() throws Exception {
        // Registra User 1
        MvcResult r1 = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("User One", "user1@example.com", "Password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        user1Token = objectMapper.readTree(r1.getResponse().getContentAsString()).get("accessToken").asText();

        // Registra User 2
        MvcResult r2 = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("User Two", "user2@example.com", "Password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        user2Token = objectMapper.readTree(r2.getResponse().getContentAsString()).get("accessToken").asText();

        // User 1 crea un veicolo
        CreateVehicleRequest vReq = new CreateVehicleRequest(
                VehicleIcon.carFront, "BMW", "Serie 3", 2022, "XY987ZT", 30000);
        MvcResult vRes = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vReq)))
                .andExpect(status().isCreated())
                .andReturn();
        user1VehicleId = objectMapper.readTree(vRes.getResponse().getContentAsString()).get("id").asText();

        // User 1 crea una manutenzione per il suo veicolo
        CreateMaintenanceRequest mReq = new CreateMaintenanceRequest(
                MaintenanceType.OIL_CHANGE, null, LocalDate.now().minusDays(1), 32000, new BigDecimal("150.00"), "User1 Olio");
        MvcResult mRes = mockMvc.perform(post("/api/v1/vehicles/" + user1VehicleId + "/maintenances")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mReq)))
                .andExpect(status().isCreated())
                .andReturn();
        user1MaintenanceId = objectMapper.readTree(mRes.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("Caso 1: User 2 non può leggere o modificare veicoli di User 1 (risponde 404 senza leak)")
    void userCannotAccessOtherUserVehicle() throws Exception {
        // User 2 cerca di leggere il veicolo di User 1 -> 404
        mockMvc.perform(get("/api/v1/vehicles/" + user1VehicleId)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // User 2 cerca di aggiornare il veicolo di User 1 -> 404
        UpdateVehicleRequest patchReq = new UpdateVehicleRequest(null, "Audi", null, null, null, null);
        mockMvc.perform(patch("/api/v1/vehicles/" + user1VehicleId)
                        .header("Authorization", "Bearer " + user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // User 2 cerca di eliminare il veicolo di User 1 -> 404
        mockMvc.perform(delete("/api/v1/vehicles/" + user1VehicleId)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Caso 1: User 2 non può leggere, creare o modificare manutenzioni di User 1 (risponde 404)")
    void userCannotAccessOtherUserMaintenance() throws Exception {
        // User 2 cerca di leggere la manutenzione di User 1 -> 404
        mockMvc.perform(get("/api/v1/maintenances/" + user1MaintenanceId)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // User 2 cerca di creare una manutenzione sul veicolo di User 1 -> 404
        CreateMaintenanceRequest mReq = new CreateMaintenanceRequest(
                MaintenanceType.BATTERY, null, LocalDate.now().minusDays(1), 35000, new BigDecimal("90.00"), null);
        mockMvc.perform(post("/api/v1/vehicles/" + user1VehicleId + "/maintenances")
                        .header("Authorization", "Bearer " + user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // User 2 cerca di eliminare la manutenzione di User 1 -> 404
        mockMvc.perform(delete("/api/v1/maintenances/" + user1MaintenanceId)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Caso 10: Sort, filtri e paginazione non consentono accesso cross-user")
    void paginationAndFilters_doNotLeakCrossUserData() throws Exception {
        // User 2 interroga la lista generale di manutenzioni: deve trovare 0 elementi
        MvcResult res = mockMvc.perform(get("/api/v1/maintenances")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.items").isEmpty())
                .andReturn();

        // User 2 prova a filtrare per il vehicleId di User 1: ottiene array vuoto e 0 elementi
        mockMvc.perform(get("/api/v1/maintenances?vehicleId=" + user1VehicleId)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.items").isEmpty());

        // User 2 prova ad accedere allo storico veicolo di User 1: riceve 404
        mockMvc.perform(get("/api/v1/vehicles/" + user1VehicleId + "/maintenances")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Targa univoca per proprietario: due utenti diversi possono avere la stessa targa")
    void plateUniquePerOwner_crossUserAllowed_sameUserRejected() throws Exception {
        // User 2 registra un veicolo con la STESSA targa di User 1 ("XY987ZT") -> successo
        CreateVehicleRequest user2Vehicle = new CreateVehicleRequest(
                VehicleIcon.car, "Mercedes", "Classe A", 2021, "xy987zt", 25000);
        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user2Vehicle)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plate").value("XY987ZT"));

        // User 1 prova a registrare un secondo veicolo con la propria stessa targa -> 409
        CreateVehicleRequest user1Duplicate = new CreateVehicleRequest(
                VehicleIcon.truck, "Iveco", "Daily", 2020, "XY987ZT", 100000);
        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user1Duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATE_ALREADY_EXISTS"));
    }
}
