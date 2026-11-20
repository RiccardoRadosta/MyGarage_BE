package com.ilmiogarage.service;

import com.ilmiogarage.dto.maintenance.AttachmentMetadataResponse;
import com.ilmiogarage.exception.ResourceNotFoundException;
import com.ilmiogarage.exception.UpgradeRequiredException;
import com.ilmiogarage.model.entity.MaintenanceAttachment;
import com.ilmiogarage.model.entity.MaintenanceRecord;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.model.entity.Vehicle;
import com.ilmiogarage.model.enums.MaintenanceType;
import com.ilmiogarage.model.enums.VehicleIcon;
import com.ilmiogarage.repository.MaintenanceAttachmentRepository;
import com.ilmiogarage.repository.MaintenanceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private MaintenanceAttachmentRepository attachmentRepository;

    @Mock
    private MaintenanceRecordRepository maintenanceRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private AttachmentService attachmentService;

    private User owner;
    private UUID ownerId;
    private Vehicle vehicle;
    private MaintenanceRecord record;
    private UUID maintenanceId;

    private static final byte[] VALID_PDF_BYTES = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        owner = new User(ownerId, "Mario Rossi", "mario@example.com", "hash");

        vehicle = new Vehicle(owner, VehicleIcon.car, "Fiat", "500", 2020, "AB123CD", 30000, true);
        vehicle.setId(UUID.randomUUID());

        maintenanceId = UUID.randomUUID();
        record = new MaintenanceRecord(owner, vehicle, MaintenanceType.SERVICE, null,
                LocalDate.now().minusDays(1), 30000, new BigDecimal("100.00"), "Tagliando");
        record.setId(maintenanceId);
    }

    @Test
    @DisplayName("Utente Free che tenta di caricare allegato riceve UpgradeRequiredException")
    void uploadAttachment_freeUser_throwsUpgradeRequired() {
        MockMultipartFile file = new MockMultipartFile("file", "fattura.pdf", "application/pdf", VALID_PDF_BYTES);

        when(maintenanceRepository.findByIdAndOwnerId(maintenanceId, ownerId)).thenReturn(Optional.of(record));
        when(subscriptionService.isPremium(ownerId)).thenReturn(false);

        assertThatThrownBy(() -> attachmentService.uploadAttachment(maintenanceId, ownerId, file))
                .isInstanceOf(UpgradeRequiredException.class)
                .hasMessageContaining("riservati agli utenti Premium");

        verify(attachmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Utente Premium può caricare allegato con successo")
    void uploadAttachment_premiumUser_success() {
        MockMultipartFile file = new MockMultipartFile("file", "fattura.pdf", "application/pdf", VALID_PDF_BYTES);

        when(maintenanceRepository.findByIdAndOwnerId(maintenanceId, ownerId)).thenReturn(Optional.of(record));
        when(subscriptionService.isPremium(ownerId)).thenReturn(true);
        when(attachmentRepository.findByMaintenanceId(maintenanceId)).thenReturn(Optional.empty());
        when(attachmentRepository.save(any(MaintenanceAttachment.class))).thenAnswer(i -> {
            MaintenanceAttachment a = i.getArgument(0);
            a.setId(UUID.randomUUID());
            a.setCreatedAt(Instant.now());
            a.setUpdatedAt(Instant.now());
            return a;
        });

        AttachmentMetadataResponse response = attachmentService.uploadAttachment(maintenanceId, ownerId, file);

        assertThat(response).isNotNull();
        assertThat(response.fileName()).isEqualTo("fattura.pdf");
        assertThat(response.contentType()).isEqualTo("application/pdf");
        verify(attachmentRepository).save(any(MaintenanceAttachment.class));
    }

    @Test
    @DisplayName("Utente Free che tenta di eliminare allegato riceve UpgradeRequiredException")
    void deleteAttachment_freeUser_throwsUpgradeRequired() {
        when(maintenanceRepository.findByIdAndOwnerId(maintenanceId, ownerId)).thenReturn(Optional.of(record));
        when(subscriptionService.isPremium(ownerId)).thenReturn(false);

        assertThatThrownBy(() -> attachmentService.deleteAttachment(maintenanceId, ownerId))
                .isInstanceOf(UpgradeRequiredException.class)
                .hasMessageContaining("riservata agli utenti Premium");

        verify(attachmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Utente Premium può eliminare allegato")
    void deleteAttachment_premiumUser_success() {
        MaintenanceAttachment attachment = new MaintenanceAttachment();
        attachment.setId(UUID.randomUUID());
        attachment.setMaintenance(record);

        when(maintenanceRepository.findByIdAndOwnerId(maintenanceId, ownerId)).thenReturn(Optional.of(record));
        when(subscriptionService.isPremium(ownerId)).thenReturn(true);
        when(attachmentRepository.findByMaintenanceId(maintenanceId)).thenReturn(Optional.of(attachment));

        attachmentService.deleteAttachment(maintenanceId, ownerId);

        verify(attachmentRepository).delete(attachment);
    }

    @Test
    @DisplayName("Utente Free può consultare metadati e contenuto di allegati esistenti")
    void getAttachment_freeUser_success() {
        MaintenanceAttachment attachment = new MaintenanceAttachment();
        attachment.setId(UUID.randomUUID());
        attachment.setMaintenance(record);
        attachment.setFileName("fattura.pdf");
        attachment.setContentType("application/pdf");
        attachment.setFileSize(VALID_PDF_BYTES.length);
        attachment.setData(VALID_PDF_BYTES);
        attachment.setCreatedAt(Instant.now());
        attachment.setUpdatedAt(Instant.now());

        when(maintenanceRepository.findByIdAndOwnerId(maintenanceId, ownerId)).thenReturn(Optional.of(record));
        when(attachmentRepository.findByMaintenanceId(maintenanceId)).thenReturn(Optional.of(attachment));

        AttachmentMetadataResponse meta = attachmentService.getAttachmentMetadata(maintenanceId, ownerId);
        assertThat(meta.fileName()).isEqualTo("fattura.pdf");

        AttachmentService.AttachmentContent content = attachmentService.getAttachmentContent(maintenanceId, ownerId);
        assertThat(content.data()).isEqualTo(VALID_PDF_BYTES);
    }

    @Test
    @DisplayName("Accesso ad allegato di intervento non appartenente all'utente restituisce 404 senza information leakage")
    void getAttachment_otherUserRecord_throwsResourceNotFound() {
        when(maintenanceRepository.findByIdAndOwnerId(maintenanceId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attachmentService.getAttachmentMetadata(maintenanceId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
