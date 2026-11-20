package com.ilmiogarage.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilmiogarage.dto.auth.RegisterRequest;
import com.ilmiogarage.dto.maintenance.CreateMaintenanceRequest;
import com.ilmiogarage.dto.maintenance.UpdateMaintenanceRequest;
import com.ilmiogarage.dto.vehicle.CreateVehicleRequest;
import com.ilmiogarage.dto.vehicle.UpdateVehicleRequest;
import com.ilmiogarage.model.entity.MaintenanceRecord;
import com.ilmiogarage.model.entity.PremiumGrant;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.model.entity.UserSubscription;
import com.ilmiogarage.model.entity.Vehicle;
import com.ilmiogarage.model.enums.GrantType;
import com.ilmiogarage.model.enums.MaintenanceType;
import com.ilmiogarage.model.enums.SubscriptionStatus;
import com.ilmiogarage.model.enums.VehicleIcon;
import com.ilmiogarage.repository.MaintenanceRecordRepository;
import com.ilmiogarage.repository.PremiumGrantRepository;
import com.ilmiogarage.repository.UserRepository;
import com.ilmiogarage.repository.UserSubscriptionRepository;
import com.ilmiogarage.repository.VehicleRepository;
import com.ilmiogarage.service.VehicleService;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DowngradeAndMandatoryPrimarySelectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private MaintenanceRecordRepository maintenanceRepository;

    @Autowired
    private UserSubscriptionRepository subscriptionRepository;

    @Autowired
    private PremiumGrantRepository grantRepository;

    @Autowired
    private VehicleService vehicleService;

    private record AuthContext(User user, String token) {}

    private AuthContext createAccount(String prefix) throws Exception {
        String email = prefix + "." + UUID.randomUUID() + "@example.com";
        MvcResult res = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(prefix, email, "Password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
        User u = userRepository.findByEmail(email).orElseThrow();
        return new AuthContext(u, token);
    }

    // =========================================================================
    // 1. FREE SENZA PRECEDENTI PREMIUM CON PIÙ VEICOLI: SCELTA RICHIESTA
    // =========================================================================
    @Test
    @DisplayName("1. Free senza precedenti Premium con più veicoli: requiresPrimarySelection = true")
    void freeUser_noHistory_multipleVehicles_requiresSelection() throws Exception {
        AuthContext auth = createAccount("free.multi");
        Vehicle v1 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Fiat", "Panda", 2020, "FA001AA", 40000, false));
        Vehicle v2 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Fiat", "500", 2021, "FA002BB", 20000, false));

        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.isPremium").value(false))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(true));
    }

    // =========================================================================
    // 2. FREE CON ZERO O UN VEICOLO: SCELTA NON RICHIESTA
    // =========================================================================
    @Test
    @DisplayName("2. Free con zero o un veicolo: requiresPrimarySelection = false")
    void freeUser_zeroOrOneVehicle_doesNotRequireSelection() throws Exception {
        AuthContext auth = createAccount("free.single");

        // Zero veicoli
        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));

        // 1 veicolo
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Fiat", "Panda", 2020, "FA101AA", 40000, true));
        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));
    }

    // =========================================================================
    // 3. PREMIUM CON PIÙ VEICOLI: SCELTA NON RICHIESTA
    // =========================================================================
    @Test
    @DisplayName("3. Premium con più veicoli: requiresPrimarySelection = false")
    void premiumUser_multipleVehicles_doesNotRequireSelection() throws Exception {
        AuthContext auth = createAccount("prem.multi");
        grantRepository.save(new PremiumGrant(auth.user(), GrantType.TESTER, Instant.now(), "Admin", null, null));

        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Audi", "A3", 2020, "AU101AA", 40000, true));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Audi", "A4", 2021, "AU102BB", 20000, false));

        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.isPremium").value(true))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));
    }

    // =========================================================================
    // 4. SUBSCRIPTION ACTIVE SCADUTA: SCELTA RICHIESTA
    // =========================================================================
    @Test
    @DisplayName("4. Subscription scaduta con più veicoli: requiresPrimarySelection = true")
    void expiredSubscription_multipleVehicles_requiresSelection() throws Exception {
        AuthContext auth = createAccount("sub.expired");
        Instant past = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant expiredAt = Instant.now().minus(2, ChronoUnit.DAYS);

        subscriptionRepository.save(new UserSubscription(
                auth.user(), "GOOGLE_PLAY", "monthly", "token1", "order1",
                SubscriptionStatus.ACTIVE, past, expiredAt, false));

        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "BMW", "Serie 1", 2020, "BM101AA", 50000, true));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "BMW", "Serie 3", 2021, "BM102BB", 30000, false));

        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.isPremium").value(false))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(true));
    }

    // =========================================================================
    // 5. SUBSCRIPTION ON_HOLD: SCELTA RICHIESTA ANCHE CON EXPIRES_AT FUTURO
    // =========================================================================
    @Test
    @DisplayName("5. Subscription ON_HOLD con expiresAt futuro: requiresPrimarySelection = true")
    void onHoldSubscription_futureExpiry_requiresSelection() throws Exception {
        AuthContext auth = createAccount("sub.onhold");
        Instant past = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant future = Instant.now().plus(20, ChronoUnit.DAYS);

        subscriptionRepository.save(new UserSubscription(
                auth.user(), "GOOGLE_PLAY", "monthly", "tokenHold", "orderHold",
                SubscriptionStatus.ON_HOLD, past, future, false));

        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Mercedes", "Classe A", 2020, "MB101AA", 30000, true));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Mercedes", "Classe C", 2021, "MB102BB", 40000, false));

        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.isPremium").value(false))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(true));
    }

    // =========================================================================
    // 6. SUBSCRIPTION PAUSED: SCELTA RICHIESTA ANCHE CON EXPIRES_AT FUTURO
    // =========================================================================
    @Test
    @DisplayName("6. Subscription PAUSED con expiresAt futuro: requiresPrimarySelection = true")
    void pausedSubscription_futureExpiry_requiresSelection() throws Exception {
        AuthContext auth = createAccount("sub.paused");
        Instant past = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant future = Instant.now().plus(15, ChronoUnit.DAYS);

        subscriptionRepository.save(new UserSubscription(
                auth.user(), "GOOGLE_PLAY", "monthly", "tokenPaused", "orderPaused",
                SubscriptionStatus.PAUSED, past, future, false));

        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "VW", "Polo", 2020, "VW101AA", 30000, true));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "VW", "Golf", 2021, "VW102BB", 40000, false));

        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.isPremium").value(false))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(true));
    }

    // =========================================================================
    // 7. SUBSCRIPTION CANCELED ANCORA VALIDA: SCELTA NON RICHIESTA
    // =========================================================================
    @Test
    @DisplayName("7. Subscription CANCELED con expiresAt futuro: requiresPrimarySelection = false (ancora Premium)")
    void canceledSubscription_futureExpiry_doesNotRequireSelection() throws Exception {
        AuthContext auth = createAccount("sub.canceled");
        Instant past = Instant.now().minus(5, ChronoUnit.DAYS);
        Instant future = Instant.now().plus(25, ChronoUnit.DAYS);

        subscriptionRepository.save(new UserSubscription(
                auth.user(), "GOOGLE_PLAY", "monthly", "tokenCancel", "orderCancel",
                SubscriptionStatus.CANCELED, past, future, false));

        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Toyota", "Yaris", 2020, "TY101AA", 20000, true));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Toyota", "Corolla", 2021, "TY102BB", 30000, false));

        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.isPremium").value(true))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));
    }

    // =========================================================================
    // 8. GRANT TEMPORANEO REVOCATO: SCELTA RICHIESTA
    // =========================================================================
    @Test
    @DisplayName("8. Grant temporaneo revocato anticipatamente: requiresPrimarySelection = true")
    void temporaryGrant_revoked_requiresSelection() throws Exception {
        AuthContext auth = createAccount("grant.revoked");
        Instant grantedAt = Instant.now().minus(5, ChronoUnit.DAYS);
        Instant expiresAt = Instant.now().plus(25, ChronoUnit.DAYS);
        PremiumGrant grant = new PremiumGrant(auth.user(), GrantType.TESTER, grantedAt, "Admin", null, expiresAt);
        grant.setRevokedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        grantRepository.save(grant);

        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Seat", "Ibiza", 2020, "ST101AA", 15000, true));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Seat", "Arona", 2021, "ST102BB", 25000, false));

        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.isPremium").value(false))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(true));
    }

    // =========================================================================
    // 9. GRANT PERMANENTE ATTIVO: SCELTA NON RICHIESTA
    // =========================================================================
    @Test
    @DisplayName("9. Grant permanente attivo con più veicoli: requiresPrimarySelection = false")
    void permanentGrant_active_doesNotRequireSelection() throws Exception {
        AuthContext auth = createAccount("grant.perm");
        grantRepository.save(new PremiumGrant(auth.user(), GrantType.DEVELOPER, Instant.now().minus(10, ChronoUnit.DAYS), "Admin", null, null));

        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Alfa", "Tonale", 2022, "AR101AA", 10000, true));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Alfa", "Stelvio", 2023, "AR102BB", 20000, false));

        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.isPremium").value(true))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));
    }

    // =========================================================================
    // 10. GRANT PERMANENTE REVOCATO: SCELTA RICHIESTA
    // =========================================================================
    @Test
    @DisplayName("10. Grant permanente revocato con più veicoli: requiresPrimarySelection = true")
    void permanentGrant_revoked_requiresSelection() throws Exception {
        AuthContext auth = createAccount("grant.permrev");
        PremiumGrant grant = new PremiumGrant(auth.user(), GrantType.COLLABORATOR, Instant.now().minus(20, ChronoUnit.DAYS), "Admin", null, null);
        grant.setRevokedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        grantRepository.save(grant);

        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Ford", "Fiesta", 2020, "FF101AA", 30000, true));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Ford", "Puma", 2021, "FF102BB", 20000, false));

        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(true));
    }

    // =========================================================================
    // 11. GRANT E SUBSCRIPTION CONTEMPORANEAMENTE VALIDI: SCELTA NON RICHIESTA
    // =========================================================================
    @Test
    @DisplayName("11. Grant e subscription contemporaneamente validi: requiresPrimarySelection = false")
    void grantAndSubscription_bothActive_doesNotRequireSelection() throws Exception {
        AuthContext auth = createAccount("both.active");
        grantRepository.save(new PremiumGrant(auth.user(), GrantType.TESTER, Instant.now().minus(5, ChronoUnit.DAYS), "Admin", null, null));
        subscriptionRepository.save(new UserSubscription(
                auth.user(), "GOOGLE_PLAY", "monthly", "tokB", "ordB",
                SubscriptionStatus.ACTIVE, Instant.now().minus(2, ChronoUnit.DAYS), Instant.now().plus(28, ChronoUnit.DAYS), true));

        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Jeep", "Compass", 2021, "JC101AA", 25000, true));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Jeep", "Renegade", 2022, "JC102BB", 15000, false));

        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));
    }

    // =========================================================================
    // 12. COMPLETAMENTO DELLA SCELTA: FLAG AGGIORNATO E PERSISTENZA
    // =========================================================================
    @Test
    @DisplayName("12. Completamento scelta obbligatoria: set-primary aggiorna primario, timestamp e disattiva flag")
    void completeMandatorySelection_updatesPrimaryAndClearsFlag() throws Exception {
        AuthContext auth = createAccount("selection.done");
        Vehicle v1 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Fiat", "Panda", 2020, "SE101AA", 40000, false));
        Vehicle v2 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Fiat", "Tipo", 2021, "SE102BB", 30000, false));

        // Prima della scelta: requiresPrimarySelection = true
        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(true));

        // Esegue la scelta obbligatoria selezionando v2
        mockMvc.perform(put("/api/v1/vehicles/" + v2.getId() + "/set-primary")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrimary").value(true));

        // Dopo la scelta: requiresPrimarySelection = false
        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false))
                .andExpect(jsonPath("$.primaryVehicleId").value(v2.getId().toString()));

        User reloadedUser = userRepository.findById(auth.user().getId()).orElseThrow();
        assertThat(reloadedUser.getPrimaryVehicleSelectedAt()).isNotNull();

        Vehicle reloadedV1 = vehicleRepository.findById(v1.getId()).orElseThrow();
        Vehicle reloadedV2 = vehicleRepository.findById(v2.getId()).orElseThrow();
        assertThat(reloadedV1.isPrimary()).isFalse();
        assertThat(reloadedV2.isPrimary()).isTrue();
    }

    // =========================================================================
    // 13. SCRITTURA PRIMA DELLA SCELTA: BLOCCATA CON 403 PRIMARY_VEHICLE_SELECTION_REQUIRED
    // =========================================================================
    @Test
    @DisplayName("13. Scrittura prima della scelta: rifiutata con 403 PRIMARY_VEHICLE_SELECTION_REQUIRED")
    void writeBeforeSelection_blockedWithPrimarySelectionRequired() throws Exception {
        AuthContext auth = createAccount("write.blocked");
        Vehicle v1 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Opel", "Corsa", 2020, "OP101AA", 30000, true));
        Vehicle v2 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Opel", "Astra", 2021, "OP102BB", 20000, false));

        // Tentativo di aggiornamento veicolo (anche se marcato primary storicamente)
        UpdateVehicleRequest patchReq = new UpdateVehicleRequest(null, null, "Corsa E", null, null, 31000);
        mockMvc.perform(patch("/api/v1/vehicles/" + v1.getId())
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIMARY_VEHICLE_SELECTION_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("È necessario selezionare il veicolo principale prima di poter eseguire modifiche."));

        // Tentativo di inserimento manutenzione
        CreateMaintenanceRequest mReq = new CreateMaintenanceRequest(
                MaintenanceType.SERVICE, null, LocalDate.now().minusDays(1), 31000, new BigDecimal("150.00"), "Tagliando");
        mockMvc.perform(post("/api/v1/vehicles/" + v1.getId() + "/maintenances")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIMARY_VEHICLE_SELECTION_REQUIRED"));

        // Tentativo di cancellazione veicolo
        mockMvc.perform(delete("/api/v1/vehicles/" + v1.getId())
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIMARY_VEHICLE_SELECTION_REQUIRED"));
    }

    // =========================================================================
    // 14 & 15. SCRITTURA DOPO LA SCELTA: PRINCIPALE OK, SECONDARIO UPGRADE_REQUIRED
    // =========================================================================
    @Test
    @DisplayName("14-15. Scrittura dopo la scelta: consentita sul principale, bloccata con UPGRADE_REQUIRED sui secondari")
    void writeAfterSelection_allowedOnPrimary_blockedOnSecondary() throws Exception {
        AuthContext auth = createAccount("write.after");
        Vehicle v1 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Renault", "Clio", 2020, "RN101AA", 50000, false));
        Vehicle v2 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Renault", "Captur", 2021, "RN102BB", 30000, false));

        // Esegue scelta: seleziona v1 come primario
        mockMvc.perform(put("/api/v1/vehicles/" + v1.getId() + "/set-primary").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());

        // Modifica su principale: OK (200)
        UpdateVehicleRequest patchV1 = new UpdateVehicleRequest(null, null, "Clio V", null, null, 51000);
        mockMvc.perform(patch("/api/v1/vehicles/" + v1.getId())
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchV1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("Clio V"));

        // Manutenzione su principale: OK (201)
        CreateMaintenanceRequest mReq = new CreateMaintenanceRequest(
                MaintenanceType.OIL_CHANGE, null, LocalDate.now().minusDays(1), 51000, new BigDecimal("100.00"), "Olio");
        mockMvc.perform(post("/api/v1/vehicles/" + v1.getId() + "/maintenances")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mReq)))
                .andExpect(status().isCreated());

        // Modifica su secondario v2: Rifiutata con UPGRADE_REQUIRED (403)
        UpdateVehicleRequest patchV2 = new UpdateVehicleRequest(null, null, "Captur Zen", null, null, null);
        mockMvc.perform(patch("/api/v1/vehicles/" + v2.getId())
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchV2)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UPGRADE_REQUIRED"));

        // Secondo tentativo di set-primary su v2 (mentre Free): Rifiutato con UPGRADE_REQUIRED
        mockMvc.perform(put("/api/v1/vehicles/" + v2.getId() + "/set-primary")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UPGRADE_REQUIRED"));
    }

    // =========================================================================
    // 16. NUOVO PERIODO PREMIUM DOPO PRECEDENTE SCELTA E SECONDO DOWNGRADE
    // =========================================================================
    @Test
    @DisplayName("16. Nuovo acquisto Premium e successivo secondo downgrade: nuova scelta richiesta")
    void multiCycle_resubscriptionAndSecondDowngrade_requiresSelectionAgain() throws Exception {
        AuthContext auth = createAccount("multicycle");
        Vehicle v1 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Skoda", "Fabia", 2020, "SK101AA", 30000, false));
        Vehicle v2 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Skoda", "Octavia", 2021, "SK102BB", 40000, false));

        // Ciclo 1 Free: scelta effettuata
        mockMvc.perform(put("/api/v1/vehicles/" + v1.getId() + "/set-primary").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));

        // Utente acquista nuovo Premium nel futuro
        Instant newSubStart = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant newSubExpiry = Instant.now().plus(40, ChronoUnit.DAYS);
        UserSubscription sub = subscriptionRepository.save(new UserSubscription(
                auth.user(), "GOOGLE_PLAY", "monthly", "tokenNew", "orderNew",
                SubscriptionStatus.ACTIVE, newSubStart, newSubExpiry, false));

        // Il secondo abbonamento scade (simuliamo data passata ma successiva a primaryVehicleSelectedAt)
        sub.setStatus(SubscriptionStatus.EXPIRED);
        sub.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        subscriptionRepository.save(sub);

        // Ora l'utente è Free e ha iniziato un ciclo Premium dopo la precedente scelta: scelta richiesta di nuovo!
        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(true));
    }

    // =========================================================================
    // 17. RINNOVO DELLA STESSA SUBSCRIPTION: NESSUNA NUOVA SCELTA
    // =========================================================================
    @Test
    @DisplayName("17. Rinnovo automatico della subscription: nessun cambio di startsAt, requiresPrimarySelection = false")
    void renewal_extendsExpiry_doesNotTriggerSelection() throws Exception {
        AuthContext auth = createAccount("sub.renewal");
        Instant originalStart = Instant.now().minus(40, ChronoUnit.DAYS);
        Instant renewedExpiry = Instant.now().plus(20, ChronoUnit.DAYS);

        subscriptionRepository.save(new UserSubscription(
                auth.user(), "GOOGLE_PLAY", "monthly", "tokRen", "ordRen",
                SubscriptionStatus.ACTIVE, originalStart, renewedExpiry, true));

        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Volvo", "XC40", 2021, "VO101AA", 20000, true));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Volvo", "XC60", 2022, "VO102BB", 30000, false));

        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));
    }

    // =========================================================================
    // 18. TIMESTAMP UGUALI O STESSA PRECISIONE: SCELTA NON RIGENERATA
    // =========================================================================
    @Test
    @DisplayName("18. Timestamp identici: primaryVehicleSelectedAt == lastPremiumStartedAt non rigenera scelta")
    void exactSameTimestamp_doesNotRequireSelection() throws Exception {
        AuthContext auth = createAccount("exact.time");
        Instant exactTime = Instant.now().minus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        subscriptionRepository.save(new UserSubscription(
                auth.user(), "GOOGLE_PLAY", "monthly", "tokExact", "ordExact",
                SubscriptionStatus.EXPIRED, exactTime, exactTime.plus(30, ChronoUnit.DAYS), false));

        auth.user().setPrimaryVehicleSelectedAt(exactTime);
        userRepository.save(auth.user());

        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Peugeot", "208", 2020, "PG101AA", 30000, true));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Peugeot", "308", 2021, "PG102BB", 40000, false));

        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));
    }

    // =========================================================================
    // 19. CONCORRENZA: DUE RICHIESTE SIMULTANEE DI SCELTA OBBLIGATORIA
    // =========================================================================
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("19. Concorrenza su set-primary: due richieste simultanee dello stesso utente Free, esattamente una ha successo")
    void concurrentPrimarySelection_onlyOneSucceeds() throws Exception {
        String email = "concurrent.primary." + UUID.randomUUID() + "@example.com";
        User user = userRepository.save(new User(UUID.randomUUID(), "Concurrent Chooser", email, "passwordHash"));
        UUID userId = user.getId();

        Vehicle v1 = vehicleRepository.save(new Vehicle(user, VehicleIcon.car, "Brand1", "Model1", 2020, "CN101AA", 10000, false));
        Vehicle v2 = vehicleRepository.save(new Vehicle(user, VehicleIcon.car, "Brand2", "Model2", 2021, "CN102BB", 20000, false));

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger upgradeRequiredCount = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        UUID[] targetVehicleIds = new UUID[]{v1.getId(), v2.getId()};

        for (int i = 0; i < numberOfThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    vehicleService.setPrimaryVehicle(targetVehicleIds[index], userId);
                    successCount.incrementAndGet();
                } catch (com.ilmiogarage.exception.UpgradeRequiredException ure) {
                    upgradeRequiredCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedExceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(unexpectedExceptions).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(upgradeRequiredCount.get()).isEqualTo(1);

        List<Vehicle> vehicles = vehicleRepository.findAllByOwnerIdOrderByUpdatedAtDesc(userId);
        long primaryCount = vehicles.stream().filter(Vehicle::isPrimary).count();
        assertThat(primaryCount).isEqualTo(1);
    }

    // =========================================================================
    // 20. TENTATIVO DI SELEZIONARE UN VEICOLO ALTRUI: 404 (ANTI-IDOR)
    // =========================================================================
    @Test
    @DisplayName("20. Anti-IDOR: tentativo di selezionare come primario un veicolo di un altro utente restituisce 404")
    void setPrimary_crossUser_returns404() throws Exception {
        AuthContext userA = createAccount("user.a");
        AuthContext userB = createAccount("user.b");

        // User A ha 2 veicoli ed è in stato di scelta obbligatoria
        vehicleRepository.save(new Vehicle(userA.user(), VehicleIcon.car, "CarA1", "ModA1", 2020, "ID001AA", 10000, false));
        vehicleRepository.save(new Vehicle(userA.user(), VehicleIcon.car, "CarA2", "ModA2", 2021, "ID002BB", 20000, false));

        // User B ha un veicolo
        Vehicle vehicleB = vehicleRepository.save(new Vehicle(userB.user(), VehicleIcon.car, "CarB", "ModB", 2022, "ID003CC", 30000, false));

        // User A tenta di impostare il veicolo di User B come primario: deve ricevere 404 (non 403 o 200)
        mockMvc.perform(put("/api/v1/vehicles/" + vehicleB.getId() + "/set-primary")
                        .header("Authorization", "Bearer " + userA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        Vehicle reloadedB = vehicleRepository.findById(vehicleB.getId()).orElseThrow();
        assertThat(reloadedB.isPrimary()).isFalse();
        assertThat(reloadedB.getOwner().getId()).isEqualTo(userB.user().getId());
    }

    // =========================================================================
    // 21. UTENTE FREE CHE HA GIÀ SCELTO IL PRIMARIO E RICEVE ON_HOLD SENZA startsAt
    // =========================================================================
    @Test
    @DisplayName("21. Utente Free con scelta effettuata riceve subscription ON_HOLD senza startsAt: nessuna nuova scelta")
    void freeUser_receivesOnHoldWithoutStartsAt_doesNotRequireSelection() throws Exception {
        AuthContext auth = createAccount("onhold.nostart");
        Vehicle v1 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Brand1", "Mod1", 2020, "OH001AA", 10000, false));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Brand2", "Mod2", 2021, "OH002BB", 20000, false));

        // Utente effettua la scelta obbligatoria del primario
        mockMvc.perform(put("/api/v1/vehicles/" + v1.getId() + "/set-primary")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());

        // Verifica che la scelta sia stata registrata
        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));

        // Arriva una subscription ON_HOLD senza startsAt (es. pagamento rifiutato/in sospeso mai attivato)
        subscriptionRepository.save(new UserSubscription(
                auth.user(), "GOOGLE_PLAY", "monthly", "tokHoldNoStart", "ordHoldNoStart",
                SubscriptionStatus.ON_HOLD, null, Instant.now().plus(20, ChronoUnit.DAYS), false));

        // Nessuna nuova scelta richiesta
        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.isPremium").value(false))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));
    }

    // =========================================================================
    // 22. SUBSCRIPTION PAUSED MAI AVVIATA (startsAt == null)
    // =========================================================================
    @Test
    @DisplayName("22. Subscription PAUSED mai avviata (startsAt == null): nessuna nuova scelta richiesta")
    void freeUser_receivesPausedWithoutStartsAt_doesNotRequireSelection() throws Exception {
        AuthContext auth = createAccount("paused.nostart");
        Vehicle v1 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Brand1", "Mod1", 2020, "PZ001AA", 10000, false));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Brand2", "Mod2", 2021, "PZ002BB", 20000, false));

        mockMvc.perform(put("/api/v1/vehicles/" + v1.getId() + "/set-primary")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());

        subscriptionRepository.save(new UserSubscription(
                auth.user(), "GOOGLE_PLAY", "monthly", "tokPauseNoStart", "ordPauseNoStart",
                SubscriptionStatus.PAUSED, null, Instant.now().plus(15, ChronoUnit.DAYS), false));

        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.isPremium").value(false))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));
    }

    // =========================================================================
    // 23. SUBSCRIPTION ON_HOLD PRECEDENTEMENTE ATTIVA (startsAt VALORIZZATO)
    // =========================================================================
    @Test
    @DisplayName("23. Subscription ON_HOLD precedentemente attiva con startsAt: richiede nuova scelta al downgrade")
    void subscriptionOnHold_previouslyActiveWithStartsAt_requiresSelection() throws Exception {
        AuthContext auth = createAccount("onhold.active");
        Vehicle v1 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Brand1", "Mod1", 2020, "HA001AA", 10000, true));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Brand2", "Mod2", 2021, "HA002BB", 20000, false));

        // Utente aveva scelto il primario al tempo T1
        Instant t1Selection = Instant.now().minus(30, ChronoUnit.DAYS);
        auth.user().setPrimaryVehicleSelectedAt(t1Selection);
        userRepository.save(auth.user());

        // Successivamente è iniziato un ciclo Premium al tempo T2 > T1
        Instant t2Start = Instant.now().minus(10, ChronoUnit.DAYS);
        subscriptionRepository.save(new UserSubscription(
                auth.user(), "GOOGLE_PLAY", "monthly", "tokHoldActive", "ordHoldActive",
                SubscriptionStatus.ON_HOLD, t2Start, Instant.now().plus(20, ChronoUnit.DAYS), false));

        // Poiché startsAt (T2) > primaryVehicleSelectedAt (T1), richiede nuova scelta
        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.isPremium").value(false))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(true));
    }

    // =========================================================================
    // 24. SUBSCRIPTION EXPIRED CON startsAt VALORIZZATO
    // =========================================================================
    @Test
    @DisplayName("24. Subscription EXPIRED con startsAt valorizzato: precedente periodo Premium riconosciuto")
    void subscriptionExpired_withStartsAt_previousPremiumRecognized() throws Exception {
        AuthContext auth = createAccount("expired.start");
        Vehicle v1 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Brand1", "Mod1", 2020, "EX001AA", 10000, true));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Brand2", "Mod2", 2021, "EX002BB", 20000, false));

        Instant t1Selection = Instant.now().minus(40, ChronoUnit.DAYS);
        auth.user().setPrimaryVehicleSelectedAt(t1Selection);
        userRepository.save(auth.user());

        Instant t2Start = Instant.now().minus(20, ChronoUnit.DAYS);
        subscriptionRepository.save(new UserSubscription(
                auth.user(), "GOOGLE_PLAY", "monthly", "tokExpStart", "ordExpStart",
                SubscriptionStatus.EXPIRED, t2Start, Instant.now().minus(1, ChronoUnit.DAYS), false));

        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(true));
    }

    // =========================================================================
    // 25. SUBSCRIPTION SENZA startsAt MA CON createdAt SUCCESSIVO ALLA SCELTA
    // =========================================================================
    @Test
    @DisplayName("25. Subscription senza startsAt ma con createdAt successivo alla scelta: nessun falso nuovo ciclo")
    void subscriptionWithoutStartsAt_createdAtAfterSelection_noFalseCycle() throws Exception {
        AuthContext auth = createAccount("created.after");
        Vehicle v1 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Brand1", "Mod1", 2020, "CR001AA", 10000, false));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Brand2", "Mod2", 2021, "CR002BB", 20000, false));

        // Utente effettua la scelta
        mockMvc.perform(put("/api/v1/vehicles/" + v1.getId() + "/set-primary")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());

        // Inserimento subscription con startsAt = null; createdAt generato automaticamente da prePersist (now)
        subscriptionRepository.save(new UserSubscription(
                auth.user(), "GOOGLE_PLAY", "monthly", "tokNoStartCreated", "ordNoStartCreated",
                SubscriptionStatus.EXPIRED, null, Instant.now().minus(1, ChronoUnit.DAYS), false));

        // createdAt è successivo alla scelta, ma startsAt è null: non deve innescare un nuovo ciclo
        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));
    }

    // =========================================================================
    // 26. GRANT E SUBSCRIPTION SOVRAPPOSTI: COMPORTAMENTO PREMIUM INVARIATO
    // =========================================================================
    @Test
    @DisplayName("26. Grant e subscription sovrapposti: comportamento Premium e downgrade coerenti")
    void overlappingGrantAndSubscription_premiumBehaviorConsistent() throws Exception {
        AuthContext auth = createAccount("overlap.subgrant");
        Vehicle v1 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Brand1", "Mod1", 2020, "OV001AA", 10000, true));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Brand2", "Mod2", 2021, "OV002BB", 20000, false));

        // Utente ha contemporaneamente Grant attivo e Subscription attiva
        grantRepository.save(new PremiumGrant(auth.user(), GrantType.DEVELOPER, Instant.now().minus(10, ChronoUnit.DAYS), "Admin", null, null));
        subscriptionRepository.save(new UserSubscription(
                auth.user(), "GOOGLE_PLAY", "monthly", "tokOverlap", "ordOverlap",
                SubscriptionStatus.ACTIVE, Instant.now().minus(5, ChronoUnit.DAYS), Instant.now().plus(25, ChronoUnit.DAYS), true));

        // Entrambi attivi -> PREMIUM, requiresPrimarySelection = false
        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.isPremium").value(true))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));
    }

    // =========================================================================
    // 27. TIMESTAMP CON NANOSECONDI DIVERSI MA IDENTICI DOPO NORMALIZZAZIONE
    // =========================================================================
    @Test
    @DisplayName("27. Timestamp con nanosecondi diversi ma identici a microsecondi: nessuna nuova scelta richiesta")
    void timestampDifferentNanos_sameMicros_doesNotRequireSelection() throws Exception {
        AuthContext auth = createAccount("nanos.diff");
        Vehicle v1 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Brand1", "Mod1", 2020, "NS001AA", 10000, true));
        vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Brand2", "Mod2", 2021, "NS002BB", 20000, false));

        // startsAt con nanosecondi .123456111Z
        Instant startsAt = Instant.parse("2026-05-01T10:00:00.123456111Z");
        subscriptionRepository.save(new UserSubscription(
                auth.user(), "GOOGLE_PLAY", "monthly", "tokNanos", "ordNanos",
                SubscriptionStatus.EXPIRED, startsAt, startsAt.plus(30, ChronoUnit.DAYS), false));

        // primaryVehicleSelectedAt con nanosecondi .123456789Z
        Instant selectedAt = Instant.parse("2026-05-01T10:00:00.123456789Z");
        auth.user().setPrimaryVehicleSelectedAt(selectedAt);
        userRepository.save(auth.user());

        // Entrambi si normalizzano a .123456Z: non deve risultare isBefore
        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));
    }

    // =========================================================================
    // 28. NUOVO PERIODO PREMIUM REALMENTE AVVIATO DOPO LA SCELTA: NUOVA SCELTA AL DOWNGRADE
    // =========================================================================
    @Test
    @DisplayName("28. Nuovo periodo Premium realmente avviato dopo la scelta: nuova scelta richiesta al successivo downgrade")
    void newPremiumActuallyStartedAfterSelection_downgradeRequiresSelectionAgain() throws Exception {
        AuthContext auth = createAccount("real.cycle");
        Vehicle v1 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Brand1", "Mod1", 2020, "RC001AA", 10000, true));
        Vehicle v2 = vehicleRepository.save(new Vehicle(auth.user(), VehicleIcon.car, "Brand2", "Mod2", 2021, "RC002BB", 20000, false));

        // 1. Scelta primario iniziale al tempo T1 nel passato
        Instant t1 = Instant.now().minus(2, ChronoUnit.DAYS);
        auth.user().setPrimaryVehicleSelectedAt(t1);
        userRepository.save(auth.user());

        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));

        // 2. Acquisto Premium reale con startsAt T2 successivo a T1 ma nel passato
        Instant realStart = Instant.now().minus(1, ChronoUnit.DAYS);
        UserSubscription sub = subscriptionRepository.save(new UserSubscription(
                auth.user(), "GOOGLE_PLAY", "monthly", "tokRealCycle", "ordRealCycle",
                SubscriptionStatus.ACTIVE, realStart, Instant.now().plus(30, ChronoUnit.DAYS), true));

        // Utente è Premium -> requiresPrimarySelection = false
        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));

        // 3. Subscrizione scade -> Downgrade a Free
        sub.setStatus(SubscriptionStatus.EXPIRED);
        sub.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        subscriptionRepository.save(sub);

        // Ora l'utente è Free e il ciclo Premium (T2) è iniziato dopo la precedente scelta (T1): scelta richiesta!
        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.requiresPrimarySelection").value(true));

        // 4. Utente sceglie il veicolo 2 come primario (al tempo attuale T3 > T2)
        mockMvc.perform(put("/api/v1/vehicles/" + v2.getId() + "/set-primary")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());

        // Ora la scelta è stata rinnovata dopo l'inizio del nuovo ciclo: risolta!
        mockMvc.perform(get("/api/v1/users/me/plan").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresPrimarySelection").value(false));
    }
}
