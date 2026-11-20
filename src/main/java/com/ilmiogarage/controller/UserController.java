package com.ilmiogarage.controller;

import com.ilmiogarage.dto.subscription.UserPlanInfo;
import com.ilmiogarage.dto.user.UpdateUserRequest;
import com.ilmiogarage.dto.user.UserResponse;
import com.ilmiogarage.security.SecurityUtils;
import com.ilmiogarage.service.SubscriptionService;
import com.ilmiogarage.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Utenti", description = "Gestione del profilo dell'utente autenticato")
@SecurityRequirement(name = "BearerAuth")
public class UserController {

    private final UserService userService;
    private final SubscriptionService subscriptionService;

    public UserController(UserService userService, SubscriptionService subscriptionService) {
        this.userService = userService;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/me/plan")
    @Operation(summary = "Ottieni stato piano utente", description = "Restituisce le informazioni sul piano Free/Premium dell'utente autenticato e sul veicolo principale.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stato piano recuperato con successo"),
            @ApiResponse(responseCode = "401", description = "Non autenticato")
    })
    public ResponseEntity<UserPlanInfo> getUserPlan() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UserPlanInfo response = subscriptionService.getUserPlanInfo(currentUserId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Ottieni profilo utente", description = "Restituisce i dati del profilo dell'utente autenticato.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profilo recuperato con successo"),
            @ApiResponse(responseCode = "401", description = "Non autenticato")
    })
    public ResponseEntity<UserResponse> getProfile() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UserResponse response = userService.getProfile(currentUserId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    @Operation(summary = "Aggiorna profilo utente", description = "Permette la modifica di nome ed email dell'utente autenticato.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profilo aggiornato"),
            @ApiResponse(responseCode = "400", description = "Dati di aggiornamento non validi"),
            @ApiResponse(responseCode = "401", description = "Non autenticato"),
            @ApiResponse(responseCode = "409", description = "Nuova email già registrata da un altro account")
    })
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateUserRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UserResponse response = userService.updateProfile(currentUserId, request);
        return ResponseEntity.ok(response);
    }
}
