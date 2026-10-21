package com.ilmiogarage.controller;

import com.ilmiogarage.dto.vehicle.CreateVehicleRequest;
import com.ilmiogarage.dto.vehicle.UpdateVehicleRequest;
import com.ilmiogarage.dto.vehicle.VehicleDetailResponse;
import com.ilmiogarage.dto.vehicle.VehicleResponse;
import com.ilmiogarage.security.SecurityUtils;
import com.ilmiogarage.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehicles")
@Tag(name = "Veicoli", description = "Gestione dei veicoli del garage dell'utente")
@SecurityRequirement(name = "BearerAuth")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    @Operation(summary = "Lista veicoli", description = "Restituisce tutti i veicoli posseduti dall'utente, inclusa la sintesi dell'ultima manutenzione.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista recuperata"),
            @ApiResponse(responseCode = "401", description = "Non autenticato")
    })
    public ResponseEntity<List<VehicleResponse>> getVehicles() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        List<VehicleResponse> list = vehicleService.getAllVehicles(currentUserId);
        return ResponseEntity.ok(list);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un nuovo veicolo", description = "Aggiunge un nuovo veicolo al garage dell'utente.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Veicolo creato con successo"),
            @ApiResponse(responseCode = "400", description = "Dati veicolo non validi"),
            @ApiResponse(responseCode = "401", description = "Non autenticato"),
            @ApiResponse(responseCode = "409", description = "Targa già presente nel garage dell'utente (PLATE_ALREADY_EXISTS)")
    })
    public ResponseEntity<VehicleResponse> createVehicle(@Valid @RequestBody CreateVehicleRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        VehicleResponse response = vehicleService.createVehicle(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{vehicleId}")
    @Operation(summary = "Dettaglio veicolo", description = "Restituisce i dettagli del veicolo con conteggio manutenzioni e ultima manutenzione.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Veicolo trovato"),
            @ApiResponse(responseCode = "401", description = "Non autenticato"),
            @ApiResponse(responseCode = "404", description = "Veicolo non trovato o non appartenente all'utente")
    })
    public ResponseEntity<VehicleDetailResponse> getVehicleById(@PathVariable UUID vehicleId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        VehicleDetailResponse response = vehicleService.getVehicleById(vehicleId, currentUserId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{vehicleId}")
    @Operation(summary = "Aggiorna parzialmente veicolo", description = "Consente l'aggiornamento parziale di marca, modello, anno, targa, km o icona.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Veicolo aggiornato"),
            @ApiResponse(responseCode = "400", description = "Dati di aggiornamento non validi"),
            @ApiResponse(responseCode = "401", description = "Non autenticato"),
            @ApiResponse(responseCode = "404", description = "Veicolo non trovato"),
            @ApiResponse(responseCode = "409", description = "Targa già presente")
    })
    public ResponseEntity<VehicleResponse> updateVehicle(
            @PathVariable UUID vehicleId,
            @Valid @RequestBody UpdateVehicleRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        VehicleResponse response = vehicleService.updateVehicle(vehicleId, currentUserId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{vehicleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina veicolo", description = "Elimina il veicolo e tutti gli interventi associati in modo transazionale.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Veicolo eliminato con successo"),
            @ApiResponse(responseCode = "401", description = "Non autenticato"),
            @ApiResponse(responseCode = "404", description = "Veicolo non trovato")
    })
    public ResponseEntity<Void> deleteVehicle(@PathVariable UUID vehicleId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        vehicleService.deleteVehicle(vehicleId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
