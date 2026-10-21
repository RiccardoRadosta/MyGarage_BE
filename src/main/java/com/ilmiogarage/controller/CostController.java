package com.ilmiogarage.controller;

import com.ilmiogarage.dto.cost.CostSummaryResponse;
import com.ilmiogarage.security.SecurityUtils;
import com.ilmiogarage.service.CostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/costs")
@Tag(name = "Costi", description = "Riepilogo e aggregazione delle spese per anno e tipologia di intervento")
@SecurityRequirement(name = "BearerAuth")
public class CostController {

    private final CostService costService;

    public CostController(CostService costService) {
        this.costService = costService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Riepilogo costi e statistiche", description = "Restituisce spesa totale, spesa per l'anno specificato (o anno corrente UTC di default), suddivisione per tipologia e fino a 6 interventi recenti.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Riepilogo costi recuperato"),
            @ApiResponse(responseCode = "401", description = "Non autenticato")
    })
    public ResponseEntity<CostSummaryResponse> getCostSummary(
            @Parameter(description = "Anno di riferimento per le statistiche (es. 2026). Se omesso, viene utilizzato l'anno corrente in UTC.")
            @RequestParam(required = false) Integer year) {

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        CostSummaryResponse response = costService.getCostSummary(currentUserId, year);
        return ResponseEntity.ok(response);
    }
}
