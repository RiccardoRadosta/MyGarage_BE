package com.ilmiogarage.controller;

import com.ilmiogarage.dto.maintenance.AttachmentMetadataResponse;
import com.ilmiogarage.security.SecurityUtils;
import com.ilmiogarage.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maintenances/{maintenanceId}/attachment")
@Tag(name = "Allegati Manutenzioni", description = "Caricamento, consultazione ed eliminazione di allegati PDF e JPEG per gli interventi di manutenzione")
@SecurityRequirement(name = "BearerAuth")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Carica o sostituisce allegato", description = "Carica o aggiorna un documento PDF o immagine JPEG (max 5 MB) per l'intervento specificato.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Allegato salvato con successo",
                    content = @Content(schema = @Schema(implementation = AttachmentMetadataResponse.class))),
            @ApiResponse(responseCode = "400", description = "File non valido, formato non supportato o dimensione superiore a 5 MB"),
            @ApiResponse(responseCode = "401", description = "Non autenticato"),
            @ApiResponse(responseCode = "404", description = "Intervento non trovato")
    })
    public ResponseEntity<AttachmentMetadataResponse> uploadAttachment(
            @Parameter(description = "ID dell'intervento di manutenzione") @PathVariable UUID maintenanceId,
            @Parameter(description = "File PDF o JPEG (max 5 MB)") @RequestParam("file") MultipartFile file) {

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        AttachmentMetadataResponse response = attachmentService.uploadAttachment(maintenanceId, currentUserId, file);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Metadati allegato", description = "Restituisce i metadati dell'allegato associato all'intervento specificato (senza caricare i dati binari).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Metadati allegato recuperati"),
            @ApiResponse(responseCode = "401", description = "Non autenticato"),
            @ApiResponse(responseCode = "404", description = "Intervento o allegato non trovato")
    })
    public ResponseEntity<AttachmentMetadataResponse> getAttachmentMetadata(
            @Parameter(description = "ID dell'intervento di manutenzione") @PathVariable UUID maintenanceId) {

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        AttachmentMetadataResponse response = attachmentService.getAttachmentMetadata(maintenanceId, currentUserId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/content")
    @Operation(summary = "Scarica/visualizza contenuto allegato", description = "Restituisce il contenuto binario dell'allegato (PDF o JPEG) con il relativo Content-Type.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contenuto binario restituito con successo"),
            @ApiResponse(responseCode = "401", description = "Non autenticato"),
            @ApiResponse(responseCode = "404", description = "Intervento o allegato non trovato")
    })
    public ResponseEntity<byte[]> getAttachmentContent(
            @Parameter(description = "ID dell'intervento di manutenzione") @PathVariable UUID maintenanceId) {

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        AttachmentService.AttachmentContent content = attachmentService.getAttachmentContent(maintenanceId, currentUserId);

        String encodedFileName = URLEncoder.encode(content.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = "inline; filename=\"" + content.fileName().replace("\"", "\\\"") + "\"; filename*=UTF-8''" + encodedFileName;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.fileSize())
                .body(content.data());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina allegato", description = "Elimina l'allegato associato all'intervento di manutenzione senza rimuovere l'intervento stesso.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Allegato eliminato con successo"),
            @ApiResponse(responseCode = "401", description = "Non autenticato"),
            @ApiResponse(responseCode = "404", description = "Intervento o allegato non trovato")
    })
    public ResponseEntity<Void> deleteAttachment(
            @Parameter(description = "ID dell'intervento di manutenzione") @PathVariable UUID maintenanceId) {

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        attachmentService.deleteAttachment(maintenanceId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
