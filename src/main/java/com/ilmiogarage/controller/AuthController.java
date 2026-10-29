package com.ilmiogarage.controller;

import com.ilmiogarage.dto.auth.AuthResponse;
import com.ilmiogarage.dto.auth.ForgotPasswordRequest;
import com.ilmiogarage.dto.auth.GoogleLoginRequest;
import com.ilmiogarage.dto.auth.LoginRequest;
import com.ilmiogarage.dto.auth.LogoutRequest;
import com.ilmiogarage.dto.auth.RefreshRequest;
import com.ilmiogarage.dto.auth.RegisterRequest;
import com.ilmiogarage.dto.auth.ResetPasswordRequest;
import com.ilmiogarage.dto.auth.TokenRefreshResponse;
import com.ilmiogarage.dto.common.MessageResponse;
import com.ilmiogarage.security.SecurityUtils;
import com.ilmiogarage.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticazione", description = "Operazioni di registrazione, login, autenticazione Google Sign-In, refresh token e logout")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrazione nuovo utente", description = "Crea un nuovo account utente e restituisce le credenziali JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registrazione completata con successo"),
            @ApiResponse(responseCode = "400", description = "Dati di registrazione non validi"),
            @ApiResponse(responseCode = "409", description = "Email già presente a sistema (EMAIL_ALREADY_EXISTS)")
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login utente", description = "Autentica l'utente tramite email e password e restituisce i token JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login avvenuto con successo"),
            @ApiResponse(responseCode = "401", description = "Credenziali non valide")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/google")
    @Operation(summary = "Autenticazione con Google Sign-In", description = "Verifica l'ID token Google crittografico, autentica o registra l'utente ed emette i token JWT applicativi.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticazione Google avvenuta con successo"),
            @ApiResponse(responseCode = "400", description = "ID token Google mancante o non valido"),
            @ApiResponse(responseCode = "401", description = "Token Google non valido, scaduto o audience non riconosciuta"),
            @ApiResponse(responseCode = "409", description = "Account esistente con credenziali standard (prevenzione account takeover)")
    })
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse response = authService.authenticateWithGoogle(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Richiesta di recupero password", description = "Genera un token di recupero monouso e invia un'email all'indirizzo specificato se registrato.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Richiesta presa in carico (risposta generica sia per account esistenti che inesistenti)"),
            @ApiResponse(responseCode = "400", description = "Formato email non valido")
    })
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        MessageResponse response = authService.forgotPassword(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reimpostazione password con token monouso", description = "Verifica il token monouso, reimposta la password dell'utente e revoca i refresh token attivi.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reimpostata con successo"),
            @ApiResponse(responseCode = "400", description = "Token non valido, scaduto o già consumato, oppure password non conforme")
    })
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        MessageResponse response = authService.resetPassword(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Ruota e rinnova la coppia access token / refresh token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token rinnovato"),
            @ApiResponse(responseCode = "401", description = "Refresh token scaduto o non valido")
    })
    public ResponseEntity<TokenRefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenRefreshResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Logout utente", description = "Revoca il refresh token della sessione corrente. Endpoint idempotente.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logout effettuato con successo"),
            @ApiResponse(responseCode = "401", description = "Non autenticato")
    })
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        authService.logout(request, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
