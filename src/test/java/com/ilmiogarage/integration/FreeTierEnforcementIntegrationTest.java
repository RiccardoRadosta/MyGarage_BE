package com.ilmiogarage.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilmiogarage.dto.auth.RegisterRequest;
import com.ilmiogarage.dto.maintenance.CreateMaintenanceRequest;
import com.ilmiogarage.dto.maintenance.UpdateMaintenanceRequest;
import com.ilmiogarage.dto.vehicle.CreateVehicleRequest;
import com.ilmiogarage.dto.vehicle.UpdateVehicleRequest;
import com.ilmiogarage.model.entity.MaintenanceAttachment;
import com.ilmiogarage.model.entity.MaintenanceRecord;
import com.ilmiogarage.model.entity.PremiumGrant;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.model.entity.Vehicle;
import com.ilmiogarage.model.enums.GrantType;
import com.ilmiogarage.model.enums.MaintenanceType;
import com.ilmiogarage.model.enums.VehicleIcon;
import com.ilmiogarage.repository.MaintenanceAttachmentRepository;
import com.ilmiogarage.repository.MaintenanceRecordRepository;
import com.ilmiogarage.repository.PremiumGrantRepository;
import com.ilmiogarage.repository.UserRepository;
import com.ilmiogarage.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FreeTierEnforcementIntegrationTest {

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
    private MaintenanceAttachmentRepository attachmentRepository;

    @Autowired
    private PremiumGrantRepository premiumGrantRepository;

    private static final byte[] VALID_PDF_BYTES = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.UTF_8);

    private String freeUserToken;
    private User freeUser;

    private String premiumUserToken;
    private User premiumUser;

    private String otherUserToken;
    private User otherUser;

    @BeforeEach
    void setUp() throws Exception {
        // 1. Registra utente Free
        String freeEmail = "free.user." + UUID.randomUUID() + "@example.com";
        MvcResult regFree = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("Free User", freeEmail, "Password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        freeUserToken = objectMapper.readTree(regFree.getResponse().getContentAsString()).get("accessToken").asText();
        freeUser = userRepository.findByEmail(freeEmail).orElseThrow();

        // 2. Registra utente Premium (con grant permanente TESTER)
        String premEmail = "premium.user." + UUID.randomUUID() + "@example.com";
        MvcResult regPrem = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("Premium User", premEmail, "Password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        premiumUserToken = objectMapper.readTree(regPrem.getResponse().getContentAsString()).get("accessToken").asText();
        premiumUser = userRepository.findByEmail(premEmail).orElseThrow();
        premiumGrantRepository.save(new PremiumGrant(premiumUser, GrantType.TESTER, Instant.now(), "System", "Permanent Tester Grant", null));

        // 3. Registra terzo utente (per test IDOR e isolamento dati)
        String otherEmail = "other.user." + UUID.randomUUID() + "@example.com";
        MvcResult regOther = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("Other User", otherEmail, "Password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        otherUserToken = objectMapper.readTree(regOther.getResponse().getContentAsString()).get("accessToken").asText();
        otherUser = userRepository.findByEmail(otherEmail).orElseThrow();
    }

    // =========================================================================
    // CREAZIONE VEICOLI (FREE vs PREMIUM)
    // =========================================================================

    @Test
    @DisplayName("Free: primo veicolo creato con successo e diventa automaticamente principale")
    void freeUser_firstVehicle_becomesPrimary() throws Exception {
        CreateVehicleRequest request = new CreateVehicleRequest(
                VehicleIcon.carFront, "Fiat", "Panda", 2021, "AA001BB", 30000);

        MvcResult res = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + freeUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPrimary").value(true))
                .andReturn();

        UUID vId = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        Vehicle saved = vehicleRepository.findById(vId).orElseThrow();
        assertThat(saved.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("Free: secondo veicolo rifiutato con HTTP 403 e codice UPGRADE_REQUIRED")
    void freeUser_secondVehicle_rejected() throws Exception {
        // Primo veicolo: OK
        CreateVehicleRequest v1 = new CreateVehicleRequest(
                VehicleIcon.carFront, "Fiat", "Panda", 2021, "AA001BB", 30000);
        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + freeUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v1)))
                .andExpect(status().isCreated());

        // Secondo veicolo: Rifiutato
        CreateVehicleRequest v2 = new CreateVehicleRequest(
                VehicleIcon.motorbike, "Honda", "SH 150", 2022, "CC002DD", 10000);
        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + freeUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v2)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UPGRADE_REQUIRED"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Il piano Free consente di gestire al massimo un veicolo. Effettua l'upgrade a Premium per aggiungere altri veicoli."));

        assertThat(vehicleRepository.countByOwnerId(freeUser.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Premium: creazione di più veicoli consentita; solo il primo è principale")
    void premiumUser_multipleVehicles_allowed() throws Exception {
        CreateVehicleRequest v1 = new CreateVehicleRequest(
                VehicleIcon.carFront, "BMW", "Serie 3", 2022, "AA111BB", 25000);
        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + premiumUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPrimary").value(true));

        CreateVehicleRequest v2 = new CreateVehicleRequest(
                VehicleIcon.motorbike, "Ducati", "Monster", 2023, "CC222DD", 5000);
        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + premiumUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPrimary").value(false));

        assertThat(vehicleRepository.countByOwnerId(premiumUser.getId())).isEqualTo(2);
    }

    // =========================================================================
    // UTENTE FREE CON PIÙ VEICOLI PREESISTENTI (RETROCOMPATIBILITÀ)
    // =========================================================================

    @Test
    @DisplayName("Free con veicoli preesistenti: non vengono cancellati, consultazione OK, creazione ulteriore rifiutata")
    void freeUser_preExistingMultipleVehicles_preservedAndRestricted() throws Exception {
        // Inserimento diretto a DB di 2 veicoli preesistenti
        Vehicle v1 = new Vehicle(freeUser, VehicleIcon.car, "Toyota", "Yaris", 2019, "TO001AA", 60000, true);
        Vehicle v2 = new Vehicle(freeUser, VehicleIcon.motorbike, "Yamaha", "TMAX", 2020, "TO002BB", 25000, false);
        vehicleRepository.save(v1);
        vehicleRepository.save(v2);

        // Consultazione lista: vede entrambi i veicoli
        mockMvc.perform(get("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Creazione nuovo veicolo: rifiutata
        CreateVehicleRequest v3 = new CreateVehicleRequest(
                VehicleIcon.van, "Ford", "Transit", 2018, "TO003CC", 120000);
        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + freeUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v3)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UPGRADE_REQUIRED"));
    }

    // =========================================================================
    // MODIFICA, KM, ELIMINAZIONE E CAMBIO PRINCIPALE
    // =========================================================================

    @Test
    @DisplayName("Free: modifica e aggiornamento KM consentiti solo sul veicolo principale")
    void freeUser_updateVehicleAndMileage_primaryOnly() throws Exception {
        Vehicle primary = vehicleRepository.save(new Vehicle(freeUser, VehicleIcon.car, "Renault", "Clio", 2020, "RE001AA", 50000, true));
        Vehicle secondary = vehicleRepository.save(new Vehicle(freeUser, VehicleIcon.motorbike, "Honda", "CB500", 2021, "RE002BB", 15000, false));
        freeUser.setPrimaryVehicleSelectedAt(Instant.now());
        userRepository.save(freeUser);

        // Modifica su principale: OK
        UpdateVehicleRequest patchPrimary = new UpdateVehicleRequest(VehicleIcon.carFront, "Renault", "Clio V", 2020, "RE001AA", 52000);
        mockMvc.perform(patch("/api/v1/vehicles/" + primary.getId())
                        .header("Authorization", "Bearer " + freeUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchPrimary)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("Clio V"))
                .andExpect(jsonPath("$.mileage").value(52000));

        // Modifica su secondario: Rifiutata 403
        UpdateVehicleRequest patchSecondary = new UpdateVehicleRequest(null, null, "CB500F", null, null, 16000);
        mockMvc.perform(patch("/api/v1/vehicles/" + secondary.getId())
                        .header("Authorization", "Bearer " + freeUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchSecondary)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UPGRADE_REQUIRED"));
    }

    @Test
    @DisplayName("Free: eliminazione veicolo rifiutata sia per principale che secondario")
    void freeUser_deleteVehicle_forbidden() throws Exception {
        Vehicle primary = vehicleRepository.save(new Vehicle(freeUser, VehicleIcon.car, "Audi", "A3", 2020, "AU001AA", 40000, true));
        Vehicle secondary = vehicleRepository.save(new Vehicle(freeUser, VehicleIcon.car, "Audi", "A1", 2021, "AU002BB", 20000, false));
        freeUser.setPrimaryVehicleSelectedAt(Instant.now());
        userRepository.save(freeUser);

        // Eliminazione principale: Rifiutata
        mockMvc.perform(delete("/api/v1/vehicles/" + primary.getId())
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UPGRADE_REQUIRED"));

        // Eliminazione secondario: Rifiutata
        mockMvc.perform(delete("/api/v1/vehicles/" + secondary.getId())
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UPGRADE_REQUIRED"));

        assertThat(vehicleRepository.existsById(primary.getId())).isTrue();
        assertThat(vehicleRepository.existsById(secondary.getId())).isTrue();
    }

    @Test
    @DisplayName("Free: cambio veicolo principale rifiutato (consentito solo a Premium)")
    void freeUser_setPrimary_forbidden() throws Exception {
        Vehicle primary = vehicleRepository.save(new Vehicle(freeUser, VehicleIcon.car, "Seat", "Ibiza", 2020, "SE001AA", 45000, true));
        Vehicle secondary = vehicleRepository.save(new Vehicle(freeUser, VehicleIcon.car, "Seat", "Leon", 2021, "SE002BB", 30000, false));
        freeUser.setPrimaryVehicleSelectedAt(Instant.now());
        userRepository.save(freeUser);

        // Free tenta di impostare secondario come principale: 403
        mockMvc.perform(put("/api/v1/vehicles/" + secondary.getId() + "/set-primary")
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UPGRADE_REQUIRED"));

        // Premium imposta secondario come principale: 200 OK
        Vehicle pV1 = vehicleRepository.save(new Vehicle(premiumUser, VehicleIcon.car, "Porsche", "Cayman", 2021, "PO001AA", 20000, true));
        Vehicle pV2 = vehicleRepository.save(new Vehicle(premiumUser, VehicleIcon.car, "Porsche", "911", 2022, "PO002BB", 10000, false));

        mockMvc.perform(put("/api/v1/vehicles/" + pV2.getId() + "/set-primary")
                        .header("Authorization", "Bearer " + premiumUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrimary").value(true));

        Vehicle reloadedV1 = vehicleRepository.findById(pV1.getId()).orElseThrow();
        Vehicle reloadedV2 = vehicleRepository.findById(pV2.getId()).orElseThrow();
        assertThat(reloadedV1.isPrimary()).isFalse();
        assertThat(reloadedV2.isPrimary()).isTrue();
    }

    // =========================================================================
    // MANUTENZIONI, BOLLO E ASSICURAZIONE
    // =========================================================================

    @Test
    @DisplayName("Free: creazione, modifica ed eliminazione manutenzioni consentite solo sul principale (inclusi BOLLO e INSURANCE)")
    void freeUser_maintenanceOperations_primaryOnly() throws Exception {
        Vehicle primary = vehicleRepository.save(new Vehicle(freeUser, VehicleIcon.car, "Jeep", "Renegade", 2021, "JP001AA", 40000, true));
        Vehicle secondary = vehicleRepository.save(new Vehicle(freeUser, VehicleIcon.motorbike, "Vespa", "GTS", 2022, "VP002BB", 5000, false));
        freeUser.setPrimaryVehicleSelectedAt(Instant.now());
        userRepository.save(freeUser);

        // 1. Creazione manutenzione ordinaria sul principale: OK
        CreateMaintenanceRequest mPrimary = new CreateMaintenanceRequest(
                MaintenanceType.SERVICE, null, LocalDate.now().minusDays(5), 41000, new BigDecimal("180.00"), "Tagliando");
        MvcResult mRes = mockMvc.perform(post("/api/v1/vehicles/" + primary.getId() + "/maintenances")
                        .header("Authorization", "Bearer " + freeUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mPrimary)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID mId = UUID.fromString(objectMapper.readTree(mRes.getResponse().getContentAsString()).get("id").asText());

        // 2. Creazione BOLLO e ASSICURAZIONE sul principale: OK
        CreateMaintenanceRequest bolloReq = new CreateMaintenanceRequest(
                MaintenanceType.BOLLO, null, LocalDate.now().minusDays(2), 41000, new BigDecimal("190.00"), "Bollo annuale");
        mockMvc.perform(post("/api/v1/vehicles/" + primary.getId() + "/maintenances")
                        .header("Authorization", "Bearer " + freeUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bolloReq)))
                .andExpect(status().isCreated());

        CreateMaintenanceRequest insReq = new CreateMaintenanceRequest(
                MaintenanceType.INSURANCE, null, LocalDate.now().minusDays(1), 41000, new BigDecimal("450.00"), "Assicurazione RCA");
        mockMvc.perform(post("/api/v1/vehicles/" + primary.getId() + "/maintenances")
                        .header("Authorization", "Bearer " + freeUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(insReq)))
                .andExpect(status().isCreated());

        // 3. Creazione sul secondario (qualsiasi tipo): Rifiutata 403
        CreateMaintenanceRequest mSec = new CreateMaintenanceRequest(
                MaintenanceType.TIRE_CHANGE, null, LocalDate.now().minusDays(1), 5200, new BigDecimal("120.00"), "Pneumatici");
        mockMvc.perform(post("/api/v1/vehicles/" + secondary.getId() + "/maintenances")
                        .header("Authorization", "Bearer " + freeUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mSec)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UPGRADE_REQUIRED"));

        // 4. Modifica manutenzione principale: OK
        UpdateMaintenanceRequest patchM = new UpdateMaintenanceRequest(null, null, null, null, new BigDecimal("200.00"), "Tagliando aggiornato");
        mockMvc.perform(patch("/api/v1/maintenances/" + mId)
                        .header("Authorization", "Bearer " + freeUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchM)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cost").value(200.00));

        // 5. Eliminazione manutenzione principale: OK (204)
        mockMvc.perform(delete("/api/v1/maintenances/" + mId)
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isNoContent());

        // 6. Modifica ed eliminazione su intervento di veicolo secondario: Rifiutata
        MaintenanceRecord secRecord = maintenanceRepository.save(new MaintenanceRecord(
                freeUser, secondary, MaintenanceType.SERVICE, null, LocalDate.now().minusDays(10), 4000, new BigDecimal("100.00"), "Storico"));

        mockMvc.perform(patch("/api/v1/maintenances/" + secRecord.getId())
                        .header("Authorization", "Bearer " + freeUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchM)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UPGRADE_REQUIRED"));

        mockMvc.perform(delete("/api/v1/maintenances/" + secRecord.getId())
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UPGRADE_REQUIRED"));
    }

    // =========================================================================
    // CONSULTAZIONE DATI (GET: TUTTI GLI UTENTI POSSONO CONSULTARE TUTTO)
    // =========================================================================

    @Test
    @DisplayName("Free: consultazione storico, costi e dashboard permessa su tutti i propri veicoli")
    void freeUser_readOperations_unrestricted() throws Exception {
        Vehicle primary = vehicleRepository.save(new Vehicle(freeUser, VehicleIcon.car, "Volvo", "XC40", 2021, "VO001AA", 30000, true));
        Vehicle secondary = vehicleRepository.save(new Vehicle(freeUser, VehicleIcon.car, "Volvo", "V40", 2018, "VO002BB", 80000, false));

        maintenanceRepository.save(new MaintenanceRecord(freeUser, primary, MaintenanceType.SERVICE, null, LocalDate.now().minusDays(5), 31000, new BigDecimal("200.00"), "Tagliando"));
        maintenanceRepository.save(new MaintenanceRecord(freeUser, secondary, MaintenanceType.SERVICE, null, LocalDate.now().minusDays(15), 79000, new BigDecimal("350.00"), "Freni"));

        // Lista veicoli
        mockMvc.perform(get("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Dettaglio veicolo secondario
        mockMvc.perform(get("/api/v1/vehicles/" + secondary.getId())
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plate").value("VO002BB"));

        // Storico manutenzioni veicolo secondario
        mockMvc.perform(get("/api/v1/vehicles/" + secondary.getId() + "/maintenances")
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));

        // Riepilogo costi
        mockMvc.perform(get("/api/v1/costs/summary")
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpent").value(550.00));

        // Dashboard Home
        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleCount").value(2))
                .andExpect(jsonPath("$.maintenanceCount").value(2))
                .andExpect(jsonPath("$.totalSpent").value(550.00));
    }

    // =========================================================================
    // ALLEGATI (UPLOAD/DELETE BLOCCATI SU FREE, DOWNLOAD/METADATA PERMESSI)
    // =========================================================================

    @Test
    @DisplayName("Free: upload e delete allegati rifiutati; visualizzazione e download allegati esistenti consentiti")
    void freeUser_attachmentRestrictions() throws Exception {
        Vehicle primary = vehicleRepository.save(new Vehicle(freeUser, VehicleIcon.car, "Ford", "Focus", 2021, "FO001AA", 40000, true));
        MaintenanceRecord record = maintenanceRepository.save(new MaintenanceRecord(
                freeUser, primary, MaintenanceType.SERVICE, null, LocalDate.now().minusDays(3), 40000, new BigDecimal("150.00"), "Tagliando"));

        // Allegato preesistente salvato a DB
        MaintenanceAttachment attachment = new MaintenanceAttachment();
        attachment.setMaintenance(record);
        attachment.setFileName("fattura_vecchia.pdf");
        attachment.setContentType("application/pdf");
        attachment.setFileSize(VALID_PDF_BYTES.length);
        attachment.setData(VALID_PDF_BYTES);
        attachmentRepository.save(attachment);

        // 1. GET metadati: OK
        mockMvc.perform(get("/api/v1/maintenances/" + record.getId() + "/attachment")
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("fattura_vecchia.pdf"))
                .andExpect(jsonPath("$.fileSize").value(VALID_PDF_BYTES.length));

        // 2. GET contenuto binario: OK
        mockMvc.perform(get("/api/v1/maintenances/" + record.getId() + "/attachment/content")
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));

        // 3. PUT upload / sostituzione: Rifiutato 403 UPGRADE_REQUIRED
        MockMultipartFile newFile = new MockMultipartFile("file", "nuova.pdf", "application/pdf", VALID_PDF_BYTES);
        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/maintenances/" + record.getId() + "/attachment")
                        .file(newFile)
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UPGRADE_REQUIRED"));

        // 4. DELETE allegato: Rifiutato 403 UPGRADE_REQUIRED
        mockMvc.perform(delete("/api/v1/maintenances/" + record.getId() + "/attachment")
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UPGRADE_REQUIRED"));
    }

    // =========================================================================
    // GRANT PERMANENTI (DEVELOPER, TESTER, COLLABORATOR)
    // =========================================================================

    @Test
    @DisplayName("Grant permanenti (DEVELOPER, TESTER, COLLABORATOR) mantengono tutte le funzionalità Premium")
    void permanentGrants_maintainFullPremiumPrivileges() throws Exception {
        for (GrantType grantType : List.of(GrantType.DEVELOPER, GrantType.TESTER, GrantType.COLLABORATOR)) {
            String email = "grant." + grantType.name().toLowerCase() + "." + UUID.randomUUID() + "@example.com";
            MvcResult reg = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RegisterRequest("Grant User", email, "Password123"))))
                    .andExpect(status().isCreated())
                    .andReturn();
            String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("accessToken").asText();
            User user = userRepository.findByEmail(email).orElseThrow();

            // Grant permanente senza scadenza
            premiumGrantRepository.save(new PremiumGrant(user, grantType, Instant.now(), "Admin", "Permanent", null));

            // Può creare 2 veicoli
            CreateVehicleRequest v1 = new CreateVehicleRequest(VehicleIcon.car, "Brand", "Model 1", 2020, "G1" + UUID.randomUUID().toString().substring(0, 5), 10000);
            CreateVehicleRequest v2 = new CreateVehicleRequest(VehicleIcon.car, "Brand", "Model 2", 2021, "G2" + UUID.randomUUID().toString().substring(0, 5), 20000);

            mockMvc.perform(post("/api/v1/vehicles").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(v1)))
                    .andExpect(status().isCreated());
            MvcResult v2Res = mockMvc.perform(post("/api/v1/vehicles").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(v2)))
                    .andExpect(status().isCreated())
                    .andReturn();
            String v2Id = objectMapper.readTree(v2Res.getResponse().getContentAsString()).get("id").asText();

            // Può eliminare veicolo
            mockMvc.perform(delete("/api/v1/vehicles/" + v2Id).header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());
        }
    }

    // =========================================================================
    // SICUREZZA, MANIPOLAZIONE ID ED IDOR (SEMPRE 404, MAI 403)
    // =========================================================================

    @Test
    @DisplayName("Sicurezza: tentativi di accesso a veicoli/manutenzioni di altri utenti restituiscono 404 senza information leakage")
    void security_crossUserAccess_returns404Not403() throws Exception {
        Vehicle otherVehicle = vehicleRepository.save(new Vehicle(otherUser, VehicleIcon.car, "Maserati", "Ghibli", 2022, "OT001AA", 15000, true));
        MaintenanceRecord otherMaintenance = maintenanceRepository.save(new MaintenanceRecord(
                otherUser, otherVehicle, MaintenanceType.SERVICE, null, LocalDate.now().minusDays(1), 15000, new BigDecimal("500.00"), "Tagliando"));

        // Free user tenta di accedere al veicolo altrui con GET, PATCH, DELETE: deve ricevere 404 (non 403)
        mockMvc.perform(get("/api/v1/vehicles/" + otherVehicle.getId())
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        UpdateVehicleRequest patchReq = new UpdateVehicleRequest(null, null, "Quattroporte", null, null, null);
        mockMvc.perform(patch("/api/v1/vehicles/" + otherVehicle.getId())
                        .header("Authorization", "Bearer " + freeUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/vehicles/" + otherVehicle.getId())
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(put("/api/v1/vehicles/" + otherVehicle.getId() + "/set-primary")
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // Free user tenta di creare manutenzione su veicolo altrui: 404 (non 403)
        CreateMaintenanceRequest mReq = new CreateMaintenanceRequest(
                MaintenanceType.SERVICE, null, LocalDate.now().minusDays(1), 16000, new BigDecimal("100.00"), "Hack");
        mockMvc.perform(post("/api/v1/vehicles/" + otherVehicle.getId() + "/maintenances")
                        .header("Authorization", "Bearer " + freeUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // Free user tenta di modificare o eliminare manutenzione altrui: 404 (non 403)
        mockMvc.perform(delete("/api/v1/maintenances/" + otherMaintenance.getId())
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // Free user tenta upload allegato su manutenzione altrui: 404 (non 403)
        MockMultipartFile file = new MockMultipartFile("file", "hack.pdf", "application/pdf", VALID_PDF_BYTES);
        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/maintenances/" + otherMaintenance.getId() + "/attachment")
                        .file(file)
                        .header("Authorization", "Bearer " + freeUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
