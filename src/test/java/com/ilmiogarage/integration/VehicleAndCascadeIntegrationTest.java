package com.ilmiogarage.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilmiogarage.dto.auth.RegisterRequest;
import com.ilmiogarage.dto.maintenance.CreateMaintenanceRequest;
import com.ilmiogarage.dto.vehicle.CreateVehicleRequest;
import com.ilmiogarage.model.enums.MaintenanceType;
import com.ilmiogarage.model.enums.VehicleIcon;
import com.ilmiogarage.repository.MaintenanceRecordRepository;
import com.ilmiogarage.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VehicleAndCascadeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private MaintenanceRecordRepository maintenanceRepository;

    @Autowired
    private com.ilmiogarage.repository.UserRepository userRepository;

    @Autowired
    private com.ilmiogarage.repository.RefreshTokenRepository refreshTokenRepository;

    private String token;

    @AfterEach
    void tearDown() {
        maintenanceRepository.deleteAll();
        vehicleRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("Cascade User", "cascade@example.com", "Password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    @DisplayName("Caso 9: La cancellazione di un veicolo elimina i suoi interventi in modo transazionale")
    void deleteVehicle_cascadesMaintenances() throws Exception {
        // 1. Crea veicolo
        CreateVehicleRequest vReq = new CreateVehicleRequest(
                VehicleIcon.van, "Ford", "Transit", 2019, "VA123BB", 120000);
        MvcResult vRes = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String vehicleIdStr = objectMapper.readTree(vRes.getResponse().getContentAsString()).get("id").asText();
        UUID vehicleId = UUID.fromString(vehicleIdStr);

        // 2. Crea due interventi per questo veicolo
        CreateMaintenanceRequest m1 = new CreateMaintenanceRequest(
                MaintenanceType.OIL_CHANGE, null, LocalDate.now().minusDays(10), 121000, new BigDecimal("100.00"), "Olio");
        MvcResult m1Res = mockMvc.perform(post("/api/v1/vehicles/" + vehicleIdStr + "/maintenances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(m1)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID m1Id = UUID.fromString(objectMapper.readTree(m1Res.getResponse().getContentAsString()).get("id").asText());

        CreateMaintenanceRequest m2 = new CreateMaintenanceRequest(
                MaintenanceType.BRAKES, null, LocalDate.now().minusDays(5), 122000, new BigDecimal("180.00"), "Freni");
        MvcResult m2Res = mockMvc.perform(post("/api/v1/vehicles/" + vehicleIdStr + "/maintenances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(m2)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID m2Id = UUID.fromString(objectMapper.readTree(m2Res.getResponse().getContentAsString()).get("id").asText());

        assertThat(vehicleRepository.existsById(vehicleId)).isTrue();
        assertThat(maintenanceRepository.existsById(m1Id)).isTrue();
        assertThat(maintenanceRepository.existsById(m2Id)).isTrue();

        // 3. Cancella veicolo
        mockMvc.perform(delete("/api/v1/vehicles/" + vehicleIdStr)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 4. Verifica che il veicolo e tutti i suoi interventi siano stati eliminati dal DB
        assertThat(vehicleRepository.existsById(vehicleId)).isFalse();
        assertThat(maintenanceRepository.existsById(m1Id)).isFalse();
        assertThat(maintenanceRepository.existsById(m2Id)).isFalse();

        // 5. Verifica che tentativi di lettura delle risorse cancellate restituiscano 404
        mockMvc.perform(get("/api/v1/vehicles/" + vehicleIdStr)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/maintenances/" + m1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
