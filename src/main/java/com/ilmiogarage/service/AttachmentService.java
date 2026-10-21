package com.ilmiogarage.service;

import com.ilmiogarage.dto.maintenance.AttachmentMetadataResponse;
import com.ilmiogarage.exception.ErrorCode;
import com.ilmiogarage.exception.InvalidAttachmentException;
import com.ilmiogarage.exception.ResourceNotFoundException;
import com.ilmiogarage.model.entity.MaintenanceAttachment;
import com.ilmiogarage.model.entity.MaintenanceRecord;
import com.ilmiogarage.repository.MaintenanceAttachmentRepository;
import com.ilmiogarage.repository.MaintenanceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    public static final String MIME_PDF = "application/pdf";
    public static final String MIME_JPEG = "image/jpeg";

    private final MaintenanceAttachmentRepository attachmentRepository;
    private final MaintenanceRecordRepository maintenanceRepository;

    public AttachmentService(
            MaintenanceAttachmentRepository attachmentRepository,
            MaintenanceRecordRepository maintenanceRepository) {
        this.attachmentRepository = attachmentRepository;
        this.maintenanceRepository = maintenanceRepository;
    }

    @Transactional
    public AttachmentMetadataResponse uploadAttachment(UUID maintenanceId, UUID ownerId, MultipartFile file) {
        MaintenanceRecord record = maintenanceRepository.findByIdAndOwnerId(maintenanceId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervento non trovato."));

        if (file == null || file.isEmpty()) {
            throw new InvalidAttachmentException(ErrorCode.VALIDATION_ERROR, "Il file selezionato è vuoto o mancante.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidAttachmentException(ErrorCode.FILE_TOO_LARGE,
                    "La dimensione dell'allegato (" + file.getSize() + " byte) supera il limite massimo di 5 MB.");
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            log.error("Errore nella lettura dei byte del file per manutenzione {}", maintenanceId, e);
            throw new InvalidAttachmentException("Impossibile leggere il file caricato.");
        }

        // Rilevamento e validazione del formato effettivo tramite magic bytes e MIME
        String detectedContentType = validateAndDetectContentType(file.getContentType(), fileBytes);

        String originalFilename = file.getOriginalFilename();
        String safeFileName = sanitizeFileName(originalFilename, detectedContentType);

        Optional<MaintenanceAttachment> existingOpt = attachmentRepository.findByMaintenanceId(maintenanceId);
        MaintenanceAttachment attachment;

        if (existingOpt.isPresent()) {
            attachment = existingOpt.get();
            attachment.setFileName(safeFileName);
            attachment.setContentType(detectedContentType);
            attachment.setFileSize(fileBytes.length);
            attachment.setData(fileBytes);
            log.info("Sostituzione allegato per manutenzione {} (dimensione: {} byte, tipo: {})",
                    maintenanceId, fileBytes.length, detectedContentType);
        } else {
            attachment = new MaintenanceAttachment();
            attachment.setMaintenance(record);
            attachment.setFileName(safeFileName);
            attachment.setContentType(detectedContentType);
            attachment.setFileSize(fileBytes.length);
            attachment.setData(fileBytes);
            log.info("Caricamento nuovo allegato per manutenzione {} (dimensione: {} byte, tipo: {})",
                    maintenanceId, fileBytes.length, detectedContentType);
        }

        MaintenanceAttachment saved = attachmentRepository.save(attachment);
        return mapToMetadata(saved);
    }

    @Transactional(readOnly = true)
    public AttachmentMetadataResponse getAttachmentMetadata(UUID maintenanceId, UUID ownerId) {
        // Verifica ownership dell'intervento
        if (!maintenanceRepository.findByIdAndOwnerId(maintenanceId, ownerId).isPresent()) {
            throw new ResourceNotFoundException("Intervento non trovato.");
        }

        MaintenanceAttachment attachment = attachmentRepository.findByMaintenanceId(maintenanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Allegato non trovato per l'intervento specificato."));

        return mapToMetadata(attachment);
    }

    @Transactional(readOnly = true)
    public AttachmentContent getAttachmentContent(UUID maintenanceId, UUID ownerId) {
        // Verifica ownership dell'intervento
        if (!maintenanceRepository.findByIdAndOwnerId(maintenanceId, ownerId).isPresent()) {
            throw new ResourceNotFoundException("Intervento non trovato.");
        }

        MaintenanceAttachment attachment = attachmentRepository.findByMaintenanceId(maintenanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Allegato non trovato per l'intervento specificato."));

        return new AttachmentContent(
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getData()
        );
    }

    @Transactional
    public void deleteAttachment(UUID maintenanceId, UUID ownerId) {
        // Verifica ownership dell'intervento
        if (!maintenanceRepository.findByIdAndOwnerId(maintenanceId, ownerId).isPresent()) {
            throw new ResourceNotFoundException("Intervento non trovato.");
        }

        MaintenanceAttachment attachment = attachmentRepository.findByMaintenanceId(maintenanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Allegato non trovato per l'intervento specificato."));

        attachmentRepository.delete(attachment);
        log.info("Eliminato allegato {} per manutenzione {} dell'utente {}", attachment.getId(), maintenanceId, ownerId);
    }

    @Transactional(readOnly = true)
    public boolean hasAttachment(UUID maintenanceId) {
        return attachmentRepository.existsByMaintenanceId(maintenanceId);
    }

    /**
     * Valida il MIME dichiarato e i magic bytes effettivi del file.
     * Restituisce il Content-Type canonical ("application/pdf" o "image/jpeg").
     */
    private String validateAndDetectContentType(String declaredContentType, byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            throw new InvalidAttachmentException("Il file caricato è corrotto o troppo piccolo.");
        }

        boolean isPdfByMagic = isPdf(bytes);
        boolean isJpegByMagic = isJpeg(bytes);

        if (!isPdfByMagic && !isJpegByMagic) {
            throw new InvalidAttachmentException(
                    "Formato file non supportato. I file ammessi sono esclusivamente documenti PDF e immagini JPEG.");
        }

        if (isPdfByMagic) {
            if (declaredContentType != null && !declaredContentType.isBlank()
                    && !declaredContentType.equalsIgnoreCase(MIME_PDF)
                    && !declaredContentType.equalsIgnoreCase("application/x-pdf")
                    && !declaredContentType.equalsIgnoreCase("application/octet-stream")) {
                throw new InvalidAttachmentException("Il tipo MIME dichiarato (" + declaredContentType + ") non corrisponde al formato PDF.");
            }
            return MIME_PDF;
        } else {
            if (declaredContentType != null && !declaredContentType.isBlank()
                    && !declaredContentType.equalsIgnoreCase(MIME_JPEG)
                    && !declaredContentType.equalsIgnoreCase("image/jpg")
                    && !declaredContentType.equalsIgnoreCase("application/octet-stream")) {
                throw new InvalidAttachmentException("Il tipo MIME dichiarato (" + declaredContentType + ") non corrisponde al formato JPEG.");
            }
            return MIME_JPEG;
        }
    }

    /**
     * Verifica magic bytes PDF: %PDF (0x25, 0x50, 0x44, 0x46)
     */
    private boolean isPdf(byte[] data) {
        return data.length >= 4
                && data[0] == 0x25
                && data[1] == 0x50
                && data[2] == 0x44
                && data[3] == 0x46;
    }

    /**
     * Verifica magic bytes JPEG: 0xFF, 0xD8 (SOI marker)
     */
    private boolean isJpeg(byte[] data) {
        return data.length >= 2
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8;
    }

    private String sanitizeFileName(String originalFilename, String detectedContentType) {
        String defaultName = MIME_PDF.equals(detectedContentType) ? "allegato.pdf" : "allegato.jpg";
        if (originalFilename == null || originalFilename.isBlank()) {
            return defaultName;
        }

        // Rimuove eventuali path traversal e cartelle
        String cleaned = StringUtils.cleanPath(originalFilename);
        if (cleaned.contains("/") || cleaned.contains("\\")) {
            cleaned = Paths.get(cleaned).getFileName().toString();
        }

        cleaned = cleaned.trim();
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) {
            return defaultName;
        }

        // Troncamento se > 255 caratteri
        if (cleaned.length() > 255) {
            String ext = "";
            int dotIdx = cleaned.lastIndexOf('.');
            if (dotIdx > 0 && dotIdx < cleaned.length() - 1) {
                ext = cleaned.substring(dotIdx);
            }
            int maxBaseLen = 255 - ext.length();
            cleaned = cleaned.substring(0, Math.max(1, maxBaseLen)) + ext;
        }

        return cleaned;
    }

    private AttachmentMetadataResponse mapToMetadata(MaintenanceAttachment entity) {
        return new AttachmentMetadataResponse(
                entity.getId(),
                entity.getMaintenance().getId(),
                entity.getFileName(),
                entity.getContentType(),
                entity.getFileSize(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public record AttachmentContent(
            String fileName,
            String contentType,
            long fileSize,
            byte[] data
    ) {
    }
}
