package com.ilmiogarage.controller;

import com.ilmiogarage.dto.common.PagedResponse;
import com.ilmiogarage.dto.maintenance.CreateMaintenanceRequest;
import com.ilmiogarage.dto.maintenance.MaintenanceDetailResponse;
import com.ilmiogarage.dto.maintenance.MaintenanceResponse;
import com.ilmiogarage.dto.maintenance.UpdateMaintenanceRequest;
import com.ilmiogarage.model.enums.MaintenanceType;
import com.ilmiogarage.security.SecurityUtils;
import com.ilmiogarage.service.MaintenanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Manutenzioni", description = "Registrazione e consultazione dello storico interventi di manutenzione svolti")
@SecurityRequirement(name = "BearerAuth")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping("/vehicles/{vehicleId}/maintenances")
    @Operation(summary = "Storico manutenzioni di un veicolo", description = "Restituisce lo storico degli interventi per il veicolo specificato con paginazione.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Storico recuperato"),
            @ApiResponse(responseCode = "401", description = "Non autenticato"),
            @ApiResponse(responseCode = "404", description = "Veicolo non trovato")
    })
    public ResponseEntity<PagedResponse<MaintenanceResponse>> getVehicleMaintenances(
            @PathVariable UUID vehicleId,
            @PageableDefault(page = 0, size = 20)
            @SortDefault(sort = "date", direction = Sort.Direction.DESC)
            Pageable pageable) {

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        PagedResponse<MaintenanceResponse> response = maintenanceService.getVehicleMaintenances(vehicleId, currentUserId, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/vehicles/{vehicleId}/maintenances")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra manutenzione per un veicolo", description = "Salva un intervento svolto. Se i km dell'intervento superano quelli correnti del veicolo, aggiorna automaticamente il chilometraggio del veicolo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Manutenzione creata con successo"),
            @ApiResponse(responseCode = "400", description = "Dati non validi (es. data futura, km negativo, OTHER senza descrizione)"),
            @ApiResponse(responseCode = "401", description = "Non autenticato"),
            @ApiResponse(responseCode = "404", description = "Veicolo non trovato")
    })
    public ResponseEntity<MaintenanceResponse> createMaintenance(
            @PathVariable UUID vehicleId,
            @Valid @RequestBody CreateMaintenanceRequest request) {

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        MaintenanceResponse response = maintenanceService.createMaintenance(vehicleId, currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/maintenances/{maintenanceId}")
    @Operation(summary = "Dettaglio singolo intervento", description = "Restituisce i dati dell'intervento di manutenzione e una sintesi essenziale del veicolo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dettaglio trovato"),
            @ApiResponse(responseCode = "401", description = "Non autenticato"),
            @ApiResponse(responseCode = "404", description = "Intervento non trovato")
    })
    public ResponseEntity<MaintenanceDetailResponse> getMaintenanceById(@PathVariable UUID maintenanceId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        MaintenanceDetailResponse response = maintenanceService.getMaintenanceById(maintenanceId, currentUserId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/maintenances")
    @Operation(summary = "Lista generale interventi filtrabile", description = "Lista filtrabile per veicolo, tipo e intervallo di date, con paginazione.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista filtrata recuperata"),
            @ApiResponse(responseCode = "401", description = "Non autenticato")
    })
    public ResponseEntity<PagedResponse<MaintenanceResponse>> getMaintenances(
            @Parameter(description = "ID veicolo opzionale")
            @RequestParam(required = false) UUID vehicleId,
            @Parameter(description = "Tipo di manutenzione opzionale")
            @RequestParam(required = false) MaintenanceType type,
            @Parameter(description = "Data minima (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Data massima (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(page = 0, size = 20)
            @SortDefault(sort = "date", direction = Sort.Direction.DESC)
            Pageable pageable) {

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        PagedResponse<MaintenanceResponse> response = maintenanceService.getMaintenancesFiltered(
                currentUserId, vehicleId, type, from, to, pageable);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/maintenances/{maintenanceId}")
    @Operation(summary = "Aggiorna manutenzione", description = "Aggiorna parzialmente i dati dell'intervento. Non consente di spostare l'intervento ad altro veicolo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Intervento aggiornato"),
            @ApiResponse(responseCode = "400", description = "Dati di aggiornamento non validi"),
            @ApiResponse(responseCode = "401", description = "Non autenticato"),
            @ApiResponse(responseCode = "404", description = "Intervento non trovato")
    })
    public ResponseEntity<MaintenanceResponse> updateMaintenance(
            @PathVariable UUID maintenanceId,
            @Valid @RequestBody UpdateMaintenanceRequest request) {

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        MaintenanceResponse response = maintenanceService.updateMaintenance(maintenanceId, currentUserId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/maintenances/{maintenanceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina manutenzione", description = "Elimina la registrazione dell'intervento senza decrementare il chilometraggio del veicolo.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Intervento eliminato con successo"),
            @ApiResponse(responseCode = "401", description = "Non autenticato"),
            @ApiResponse(responseCode = "404", description = "Intervento non trovato")
    })
    public ResponseEntity<Void> deleteMaintenance(@PathVariable UUID maintenanceId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        maintenanceService.deleteMaintenance(maintenanceId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
