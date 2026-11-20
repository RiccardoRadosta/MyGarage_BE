package com.ilmiogarage.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilmiogarage.dto.auth.RegisterRequest;
import com.ilmiogarage.dto.vehicle.CreateVehicleRequest;
import com.ilmiogarage.model.entity.PremiumGrant;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.model.entity.UserSubscription;
import com.ilmiogarage.model.entity.Vehicle;
import com.ilmiogarage.model.enums.GrantType;
import com.ilmiogarage.model.enums.SubscriptionStatus;
import com.ilmiogarage.model.enums.VehicleIcon;
import com.ilmiogarage.repository.PremiumGrantRepository;
import com.ilmiogarage.repository.UserRepository;
import com.ilmiogarage.repository.UserSubscriptionRepository;
import com.ilmiogarage.repository.VehicleRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlanAndPrimaryVehicleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PremiumGrantRepository premiumGrantRepository;

    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;

    private record AuthContext(String token, UUID userId) {}

    private AuthContext registerUser(String name, String email) throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(name, email, "Password123!"))))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("accessToken").asText();
        String userIdStr = objectMapper.readTree(reg.getResponse().getContentAsString()).get("user").get("id").asText();
        return new AuthContext(token, UUID.fromString(userIdStr));
    }

    private String createVehicle(String token, String plate, String model) throws Exception {
        CreateVehicleRequest req = new CreateVehicleRequest(VehicleIcon.car, "Toyota", model, 2021, plate, 30000);
        MvcResult res = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private void makePremium(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        premiumGrantRepository.save(new PremiumGrant(user, GrantType.DEVELOPER, Instant.now(), "System", "Test grant", null));
    }

    @Test
    @DisplayName("GET /api/v1/users/me/plan: Utente Free senza abbonamenti né veicolo principale")
    void getPlan_freeUserWithoutSubscriptions_returnsFreeTier() throws Exception {
        AuthContext user = registerUser("Free User", "free.user@example.com");

        mockMvc.perform(get("/api/v1/users/me/plan")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.isPremium").value(false))
                .andExpect(jsonPath("$.origin").value("FREE_TIER"))
                .andExpect(jsonPath("$.grantType").doesNotExist())
                .andExpect(jsonPath("$.subscriptionStatus").doesNotExist())
                .andExpect(jsonPath("$.productId").doesNotExist())
                .andExpect(jsonPath("$.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.autoRenewing").value(false))
                .andExpect(jsonPath("$.isPermanent").value(false))
                .andExpect(jsonPath("$.primaryVehicleId").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/users/me/plan: Utente Premium da abbonamento Google Play attivo")
    void getPlan_premiumFromSubscription() throws Exception {
        AuthContext user = registerUser("Subscriber User", "subscriber@example.com");
        User userEntity = userRepository.findById(user.userId()).orElseThrow();
        Instant now = Instant.now();
        Instant futureExpiry = now.plus(30, ChronoUnit.DAYS);

        UserSubscription sub = new UserSubscription(
                userEntity, "GOOGLE_PLAY", "mygarage_premium_monthly", "GPA.token999", "GPA.999-order",
                SubscriptionStatus.ACTIVE, now.minus(2, ChronoUnit.DAYS), futureExpiry, true
        );
        userSubscriptionRepository.saveAndFlush(sub);

        mockMvc.perform(get("/api/v1/users/me/plan")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.isPremium").value(true))
                .andExpect(jsonPath("$.origin").value("GOOGLE_PLAY"))
                .andExpect(jsonPath("$.productId").value("mygarage_premium_monthly"))
                .andExpect(jsonPath("$.subscriptionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.autoRenewing").value(true))
                .andExpect(jsonPath("$.isPermanent").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/users/me/plan: Utente Premium da grant permanente DEVELOPER")
    void getPlan_premiumFromPermanentGrant() throws Exception {
        AuthContext user = registerUser("Developer User", "dev@example.com");
        User userEntity = userRepository.findById(user.userId()).orElseThrow();
        Instant now = Instant.now();

        PremiumGrant grant = new PremiumGrant(
                userEntity, GrantType.DEVELOPER, now.minus(5, ChronoUnit.DAYS), "SYSTEM", "Lead dev", null
        );
        premiumGrantRepository.saveAndFlush(grant);

        mockMvc.perform(get("/api/v1/users/me/plan")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.isPremium").value(true))
                .andExpect(jsonPath("$.origin").value("GRANT"))
                .andExpect(jsonPath("$.grantType").value("DEVELOPER"))
                .andExpect(jsonPath("$.isPermanent").value(true))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    @DisplayName("PUT /api/v1/vehicles/{id}/set-primary: Impostazione del primo veicolo principale e recupero in GET plan")
    void setPrimary_firstVehicle_andRetrieveInPlan() throws Exception {
        AuthContext user = registerUser("Owner 1", "owner1@example.com");
        makePremium(user.userId());
        String vId = createVehicle(user.token(), "AA111AA", "Yaris");

        // Imposta come primario
        mockMvc.perform(put("/api/v1/vehicles/" + vId + "/set-primary")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vId))
                .andExpect(jsonPath("$.isPrimary").value(true));

        // Verifica persistenza DB
        Vehicle v = vehicleRepository.findById(UUID.fromString(vId)).orElseThrow();
        assertThat(v.isPrimary()).isTrue();

        // Verifica che GET /api/v1/users/me/plan restituisca primaryVehicleId
        mockMvc.perform(get("/api/v1/users/me/plan")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryVehicleId").value(vId));
    }

    @Test
    @DisplayName("PUT /api/v1/vehicles/{id}/set-primary: Cambio del veicolo principale tra due veicoli dello stesso proprietario")
    void setPrimary_switchBetweenVehicles() throws Exception {
        AuthContext user = registerUser("Owner Switch", "switch@example.com");
        makePremium(user.userId());
        String v1Id = createVehicle(user.token(), "AA222AA", "Yaris");
        String v2Id = createVehicle(user.token(), "BB333BB", "Corolla");

        // 1. Imposta v1 come primario
        mockMvc.perform(put("/api/v1/vehicles/" + v1Id + "/set-primary")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrimary").value(true));

        assertThat(vehicleRepository.findById(UUID.fromString(v1Id)).orElseThrow().isPrimary()).isTrue();
        assertThat(vehicleRepository.findById(UUID.fromString(v2Id)).orElseThrow().isPrimary()).isFalse();

        // 2. Imposta v2 come primario (switch)
        mockMvc.perform(put("/api/v1/vehicles/" + v2Id + "/set-primary")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(v2Id))
                .andExpect(jsonPath("$.isPrimary").value(true));

        // Verifica che v1 non sia più primario e che v2 sia l'unico primario
        assertThat(vehicleRepository.findById(UUID.fromString(v1Id)).orElseThrow().isPrimary()).isFalse();
        assertThat(vehicleRepository.findById(UUID.fromString(v2Id)).orElseThrow().isPrimary()).isTrue();

        // Verifica in GET plan
        mockMvc.perform(get("/api/v1/users/me/plan")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryVehicleId").value(v2Id));
    }

    @Test
    @DisplayName("PUT /api/v1/vehicles/{id}/set-primary: Veicolo inesistente -> 404 NOT_FOUND")
    void setPrimary_nonExistentVehicle_returns404() throws Exception {
        AuthContext user = registerUser("Owner 404", "owner404@example.com");
        makePremium(user.userId());
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/vehicles/" + randomId + "/set-primary")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Veicolo non trovato."));
    }

    @Test
    @DisplayName("PUT /api/v1/vehicles/{id}/set-primary: Tentativo di selezionare veicolo di un altro utente -> 404 NOT_FOUND")
    void setPrimary_otherUserVehicle_returns404() throws Exception {
        AuthContext userA = registerUser("User A", "usera@example.com");
        makePremium(userA.userId());
        AuthContext userB = registerUser("User B", "userb@example.com");

        String vehicleBId = createVehicle(userB.token(), "BB888BB", "Golf");

        // User A tenta di impostare come primario il veicolo di User B
        mockMvc.perform(put("/api/v1/vehicles/" + vehicleBId + "/set-primary")
                        .header("Authorization", "Bearer " + userA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Veicolo non trovato."));

        // Il veicolo di User B rimane di proprietà di User B
        assertThat(vehicleRepository.findById(UUID.fromString(vehicleBId)).orElseThrow().getOwner().getId())
                .isEqualTo(userB.userId());
    }

    @Test
    @DisplayName("Garanzia di un solo veicolo principale per proprietario tra 3 veicoli")
    void setPrimary_guaranteesExactlyOnePrimaryVehicle() throws Exception {
        AuthContext user = registerUser("Multi Vehicle User", "multiveh@example.com");
        makePremium(user.userId());
        String v1Id = createVehicle(user.token(), "CC111CC", "Aygo");
        String v2Id = createVehicle(user.token(), "CC222CC", "Yaris");
        String v3Id = createVehicle(user.token(), "CC333CC", "RAV4");

        // Imposta v1
        mockMvc.perform(put("/api/v1/vehicles/" + v1Id + "/set-primary")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk());

        // Poi imposta v3
        mockMvc.perform(put("/api/v1/vehicles/" + v3Id + "/set-primary")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk());

        List<Vehicle> allVehicles = vehicleRepository.findAllByOwnerIdOrderByUpdatedAtDesc(user.userId());
        long primaryCount = allVehicles.stream().filter(Vehicle::isPrimary).count();
        assertThat(primaryCount).isEqualTo(1);

        Vehicle primary = vehicleRepository.findByOwnerIdAndIsPrimaryTrue(user.userId()).orElseThrow();
        assertThat(primary.getId()).isEqualTo(UUID.fromString(v3Id));
    }

    @Test
    @DisplayName("Isolamento dei dati tra utenti: due utenti possono avere ciascuno il proprio veicolo principale")
    void setPrimary_dataIsolationBetweenUsers() throws Exception {
        AuthContext userA = registerUser("Iso A", "iso.a@example.com");
        makePremium(userA.userId());
        AuthContext userB = registerUser("Iso B", "iso.b@example.com");
        makePremium(userB.userId());

        String vA = createVehicle(userA.token(), "XA100AX", "Panda");
        String vB = createVehicle(userB.token(), "XB200BX", "500");

        mockMvc.perform(put("/api/v1/vehicles/" + vA + "/set-primary")
                        .header("Authorization", "Bearer " + userA.token()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/vehicles/" + vB + "/set-primary")
                        .header("Authorization", "Bearer " + userB.token()))
                .andExpect(status().isOk());

        // Entrambi gli utenti hanno il proprio veicolo primario attivo
        assertThat(vehicleRepository.findByOwnerIdAndIsPrimaryTrue(userA.userId()).orElseThrow().getId())
                .isEqualTo(UUID.fromString(vA));
        assertThat(vehicleRepository.findByOwnerIdAndIsPrimaryTrue(userB.userId()).orElseThrow().getId())
                .isEqualTo(UUID.fromString(vB));

        // GET plan riflette il rispettivo veicolo primario
        mockMvc.perform(get("/api/v1/users/me/plan")
                        .header("Authorization", "Bearer " + userA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryVehicleId").value(vA));

        mockMvc.perform(get("/api/v1/users/me/plan")
                        .header("Authorization", "Bearer " + userB.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryVehicleId").value(vB));
    }
}
