package com.ilmiogarage.controller;

import com.ilmiogarage.dto.dashboard.DashboardResponse;
import com.ilmiogarage.security.SecurityUtils;
import com.ilmiogarage.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Riepilogo generale Home per l'applicazione mobile")
@SecurityRequirement(name = "BearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Riepilogo Dashboard Home", description = "Restituisce conteggio veicoli, interventi, spesa totale, lista veicoli (con ultima manutenzione) e interventi recenti.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard recuperata"),
            @ApiResponse(responseCode = "401", description = "Non autenticato")
    })
    public ResponseEntity<DashboardResponse> getDashboard() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        DashboardResponse response = dashboardService.getDashboard(currentUserId);
        return ResponseEntity.ok(response);
    }
}
