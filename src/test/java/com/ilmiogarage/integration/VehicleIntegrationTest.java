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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VehicleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("Vehicle Tester", "veh.tester@example.com", "Password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    @DisplayName("CRUD veicolo completo con verifica sintesi ultima manutenzione e conteggio")
    void fullVehicleLifecycle() throws Exception {
        // 1. Crea veicolo
        CreateVehicleRequest createReq = new CreateVehicleRequest(
                VehicleIcon.car, "Toyota", "Yaris", 2020, "AB555CD", 45000);

        MvcResult createRes = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.brand").value("Toyota"))
                .andExpect(jsonPath("$.model").value("Yaris"))
                .andExpect(jsonPath("$.plate").value("AB555CD"))
                .andExpect(jsonPath("$.mileage").value(45000))
                .andExpect(jsonPath("$.lastMaintenance").doesNotExist())
                .andReturn();

        String vehicleId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        // 2. Lettura lista veicoli
        mockMvc.perform(get("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(vehicleId));

        // 3. Aggiunta di un intervento per popolare lastMaintenance e conteggio
        CreateMaintenanceRequest mReq = new CreateMaintenanceRequest(
                MaintenanceType.INSPECTION, null, LocalDate.now().minusDays(5), 45000, new BigDecimal("79.50"), "Revisione biennale");
        mockMvc.perform(post("/api/v1/vehicles/" + vehicleId + "/maintenances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mReq)))
                .andExpect(status().isCreated());

        // 4. Lettura dettaglio veicolo: include lastMaintenance e maintenanceCount = 1
        mockMvc.perform(get("/api/v1/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vehicleId))
                .andExpect(jsonPath("$.maintenanceCount").value(1))
                .andExpect(jsonPath("$.lastMaintenance.type").value("INSPECTION"))
                .andExpect(jsonPath("$.lastMaintenance.typeLabel").value("Revisione"))
                .andExpect(jsonPath("$.lastMaintenance.cost").value(79.50));

        // 5. Aggiornamento parziale (PATCH)
        UpdateVehicleRequest patchReq = new UpdateVehicleRequest(
                VehicleIcon.truck, "Toyota", "Hilux", 2021, "AB555CD", 46000);
        mockMvc.perform(patch("/api/v1/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("Hilux"))
                .andExpect(jsonPath("$.icon").value("truck"))
                .andExpect(jsonPath("$.mileage").value(46000));
    }
}
