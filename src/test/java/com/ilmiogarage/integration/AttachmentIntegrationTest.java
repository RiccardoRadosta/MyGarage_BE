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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AttachmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private String otherUserToken;
    private String maintenanceId;

    // Dummy PDF con firma %PDF
    private static final byte[] VALID_PDF_BYTES = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.UTF_8);

    // Dummy JPEG con SOI marker FF D8
    private static final byte[] VALID_JPEG_BYTES = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00,
            0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, (byte) 0xFF, (byte) 0xD9
    };

    @BeforeEach
    void setUp() throws Exception {
        // Registra utente principale
        String email = "attach.user." + UUID.randomUUID() + "@example.com";
        MvcResult reg = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("User One", email, "Password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        userToken = objectMapper.readTree(reg.getResponse().getContentAsString()).get("accessToken").asText();

        // Registra secondo utente (per test IDOR)
        String otherEmail = "other.user." + UUID.randomUUID() + "@example.com";
        MvcResult regOther = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("User Two", otherEmail, "Password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        otherUserToken = objectMapper.readTree(regOther.getResponse().getContentAsString()).get("accessToken").asText();

        // Crea veicolo per utente principale
        CreateVehicleRequest vReq = new CreateVehicleRequest(
                VehicleIcon.car, "Volkswagen", "Golf", 2020, "AA123BB", 45000);
        MvcResult vRes = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String vehicleId = objectMapper.readTree(vRes.getResponse().getContentAsString()).get("id").asText();

        // Crea manutenzione
        CreateMaintenanceRequest mReq = new CreateMaintenanceRequest(
                MaintenanceType.SERVICE, null, LocalDate.now().minusDays(5), 48000, new BigDecimal("200.00"), "Tagliando con fattura");
        MvcResult mRes = mockMvc.perform(post("/api/v1/vehicles/" + vehicleId + "/maintenances")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mReq)))
                .andExpect(status().isCreated())
                .andReturn();
        maintenanceId = objectMapper.readTree(mRes.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("Upload allegato PDF valido, recupero metadati, recupero binario ed eliminazione")
    void testUploadPdf_getMetadata_getContent_andDelete() throws Exception {
        // Inizialmente la manutenzione non ha allegato
        mockMvc.perform(get("/api/v1/maintenances/" + maintenanceId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAttachment").value(false));

        // 1. Upload PDF
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fattura_tagliando.pdf",
                "application/pdf",
                VALID_PDF_BYTES
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/maintenances/" + maintenanceId + "/attachment")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("fattura_tagliando.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.fileSize").value(VALID_PDF_BYTES.length));

        // Ora hasAttachment deve essere true
        mockMvc.perform(get("/api/v1/maintenances/" + maintenanceId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAttachment").value(true));

        // 2. Recupero metadati allegato
        mockMvc.perform(get("/api/v1/maintenances/" + maintenanceId + "/attachment")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("fattura_tagliando.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"));

        // 3. Download contenuto binario
        MvcResult downloadResult = mockMvc.perform(get("/api/v1/maintenances/" + maintenanceId + "/attachment/content")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn();

        byte[] downloadedBytes = downloadResult.getResponse().getContentAsByteArray();
        assertThat(downloadedBytes).isEqualTo(VALID_PDF_BYTES);

        // 4. Eliminazione allegato
        mockMvc.perform(delete("/api/v1/maintenances/" + maintenanceId + "/attachment")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        // Dopo eliminazione, la richiesta metadati da 404
        mockMvc.perform(get("/api/v1/maintenances/" + maintenanceId + "/attachment")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());

        // La manutenzione esiste ancora ma hasAttachment è false
        mockMvc.perform(get("/api/v1/maintenances/" + maintenanceId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAttachment").value(false));
    }

    @Test
    @DisplayName("Upload allegato JPEG e sostituzione con nuovo file")
    void testUploadJpegAndReplace() throws Exception {
        // 1. Upload JPEG
        MockMultipartFile jpegFile = new MockMultipartFile(
                "file",
                "ricevuta.jpg",
                "image/jpeg",
                VALID_JPEG_BYTES
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/maintenances/" + maintenanceId + "/attachment")
                        .file(jpegFile)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("ricevuta.jpg"))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"));

        // 2. Sostituzione con PDF
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "nuova_fattura.pdf",
                "application/pdf",
                VALID_PDF_BYTES
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/maintenances/" + maintenanceId + "/attachment")
                        .file(pdfFile)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("nuova_fattura.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"));

        // Verifica che il download restituisca il nuovo file PDF
        MvcResult res = mockMvc.perform(get("/api/v1/maintenances/" + maintenanceId + "/attachment/content")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn();

        assertThat(res.getResponse().getContentAsByteArray()).isEqualTo(VALID_PDF_BYTES);
    }

    @Test
    @DisplayName("File con estensione o MIME ingannevole ma magic bytes errati viene rifiutato (400)")
    void testInvalidMagicBytesRejected() throws Exception {
        // File spacciato per PDF ma contenente testo
        byte[] fakePdfBytes = "Questo è un finto pdf, in realtà solo testo".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile fakeFile = new MockMultipartFile(
                "file",
                "virus.pdf",
                "application/pdf",
                fakePdfBytes
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/maintenances/" + maintenanceId + "/attachment")
                        .file(fakeFile)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_FORMAT"));
    }

    @Test
    @DisplayName("File oltre 5 MB viene rifiutato (400 o 413)")
    void testFileTooLargeRejected() throws Exception {
        // File PDF fittizio di 5.5 MB
        byte[] largeBytes = new byte[5 * 1024 * 1024 + 1024];
        // Inseriamo magic bytes %PDF all'inizio
        System.arraycopy(VALID_PDF_BYTES, 0, largeBytes, 0, VALID_PDF_BYTES.length);

        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "documento_gigante.pdf",
                "application/pdf",
                largeBytes
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/maintenances/" + maintenanceId + "/attachment")
                        .file(largeFile)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    @DisplayName("Protezione IDOR: Utente B non può visualizzare o scaricare allegati dell'utente A")
    void testIdorProtection() throws Exception {
        // Utente A carica un file
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "privato.pdf",
                "application/pdf",
                VALID_PDF_BYTES
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/maintenances/" + maintenanceId + "/attachment")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        // Utente B tenta di accedere ai metadati
        mockMvc.perform(get("/api/v1/maintenances/" + maintenanceId + "/attachment")
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // Utente B tenta di scaricare il binario
        mockMvc.perform(get("/api/v1/maintenances/" + maintenanceId + "/attachment/content")
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // Utente B tenta di eliminare l'allegato
        mockMvc.perform(delete("/api/v1/maintenances/" + maintenanceId + "/attachment")
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
