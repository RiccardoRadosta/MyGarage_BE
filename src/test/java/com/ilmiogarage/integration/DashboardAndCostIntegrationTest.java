package com.ilmiogarage.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilmiogarage.dto.auth.RegisterRequest;
import com.ilmiogarage.dto.maintenance.CreateMaintenanceRequest;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardAndCostIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private String vehicle1Id;
    private String vehicle2Id;

    @BeforeEach
    void setUp() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("Dash Tester", "dash@example.com", "Password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("accessToken").asText();

        // Crea veicolo 1
        CreateVehicleRequest v1 = new CreateVehicleRequest(
                VehicleIcon.carFront, "BMW", "Serie 1", 2021, "AA111BB", 60000);
        MvcResult v1Res = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v1)))
                .andExpect(status().isCreated())
                .andReturn();
        vehicle1Id = objectMapper.readTree(v1Res.getResponse().getContentAsString()).get("id").asText();

        // Crea veicolo 2
        CreateVehicleRequest v2 = new CreateVehicleRequest(
                VehicleIcon.scooter, "Piaggio", "Beverly", 2022, "CC222DD", 15000);
        MvcResult v2Res = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v2)))
                .andExpect(status().isCreated())
                .andReturn();
        vehicle2Id = objectMapper.readTree(v2Res.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("Caso 6: Costo totale e aggregazioni per anno/tipo sono calcolati dai record")
    void dashboardAndCostSummary_calculatedFromRecords() throws Exception {
        int currentYear = LocalDate.now().getYear();

        // Intervento 1 nel 2025: 100.00
        CreateMaintenanceRequest m1 = new CreateMaintenanceRequest(
                MaintenanceType.OIL_CHANGE, null, LocalDate.of(2025, 5, 10), 61000, new BigDecimal("100.00"), "Olio 2025");
        mockMvc.perform(post("/api/v1/vehicles/" + vehicle1Id + "/maintenances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(m1)))
                .andExpect(status().isCreated());

        // Intervento 2 nell'anno corrente: 150.00 (Tagliando)
        CreateMaintenanceRequest m2 = new CreateMaintenanceRequest(
                MaintenanceType.SERVICE, null, LocalDate.of(currentYear, 2, 1), 63000, new BigDecimal("150.00"), "Tagliando anno corrente");
        mockMvc.perform(post("/api/v1/vehicles/" + vehicle1Id + "/maintenances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(m2)))
                .andExpect(status().isCreated());

        // Intervento 3 nell'anno corrente: 90.00 (Batteria sul secondo veicolo)
        CreateMaintenanceRequest m3 = new CreateMaintenanceRequest(
                MaintenanceType.BATTERY, null, LocalDate.of(currentYear, 4, 15), 16000, new BigDecimal("90.00"), "Batteria scooter");
        mockMvc.perform(post("/api/v1/vehicles/" + vehicle2Id + "/maintenances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(m3)))
                .andExpect(status().isCreated());

        // 1. Verifica Dashboard
        // Spesa totale attesa: 100.00 + 150.00 + 90.00 = 340.00
        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleCount").value(2))
                .andExpect(jsonPath("$.maintenanceCount").value(3))
                .andExpect(jsonPath("$.totalSpent").value(340.00))
                .andExpect(jsonPath("$.vehicles", hasSize(2)))
                .andExpect(jsonPath("$.recentMaintenances", hasSize(3)));

        // 2. Verifica Cost Summary per l'anno corrente
        // yearSpent atteso: 150.00 + 90.00 = 240.00
        // byType: SERVICE (150.00, count 1), BATTERY (90.00, count 1)
        mockMvc.perform(get("/api/v1/costs/summary?year=" + currentYear)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.year").value(currentYear))
                .andExpect(jsonPath("$.totalSpent").value(340.00))
                .andExpect(jsonPath("$.yearSpent").value(240.00))
                .andExpect(jsonPath("$.byType", hasSize(2)))
                .andExpect(jsonPath("$.recentMaintenances", hasSize(3)));

        // 3. Verifica Cost Summary per il 2025
        // yearSpent atteso: 100.00
        mockMvc.perform(get("/api/v1/costs/summary?year=2025")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.totalSpent").value(340.00))
                .andExpect(jsonPath("$.yearSpent").value(100.00))
                .andExpect(jsonPath("$.byType[0].type").value("OIL_CHANGE"))
                .andExpect(jsonPath("$.byType[0].total").value(100.00));
    }
}
