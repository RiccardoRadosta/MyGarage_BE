package com.ilmiogarage.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilmiogarage.dto.auth.ForgotPasswordRequest;
import com.ilmiogarage.dto.auth.LoginRequest;
import com.ilmiogarage.dto.auth.RefreshRequest;
import com.ilmiogarage.dto.auth.ResetPasswordRequest;
import com.ilmiogarage.exception.EmailDeliveryException;
import com.ilmiogarage.model.entity.PasswordResetToken;
import com.ilmiogarage.model.entity.RefreshToken;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.repository.PasswordResetTokenRepository;
import com.ilmiogarage.repository.RefreshTokenRepository;
import com.ilmiogarage.repository.UserRepository;
import com.ilmiogarage.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private EmailService emailService;

    private User standardUser;
    private User googleUser;

    @BeforeEach
    void setUp() {
        cleanup();
        when(emailService.isConfigured()).thenReturn(true);

        standardUser = new User(
                UUID.randomUUID(),
                "Mario Rossi",
                "mario.rossi@example.com",
                passwordEncoder.encode("OldPassword123!")
        );
        userRepository.save(standardUser);

        googleUser = new User(
                UUID.randomUUID(),
                "Google User",
                "google.user@example.com",
                null,
                "google-sub-99999"
        );
        userRepository.save(googleUser);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("1. Richiesta recupero con account esistente e password locale: token generato, salvato come hash e invio email")
    void forgotPassword_existingAccount_createsHashedTokenAndSendsEmail() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("mario.rossi@example.com");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Se l'indirizzo email inserito è associato a un account, riceverai a breve un'email con le istruzioni per reimpostare la tua password."));

        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendPasswordResetEmail(
                eq("mario.rossi@example.com"),
                eq("Mario Rossi"),
                rawTokenCaptor.capture()
        );

        String rawToken = rawTokenCaptor.getValue();
        assertThat(rawToken).isNotBlank();

        // Verifica che nel DB sia memorizzato solo l'hash SHA-256 e NON il token in chiaro
        String expectedHash = sha256(rawToken);
        Optional<PasswordResetToken> tokenInDb = passwordResetTokenRepository.findByTokenHash(expectedHash);
        assertThat(tokenInDb).isPresent();
        assertThat(tokenInDb.get().getTokenHash()).isEqualTo(expectedHash);
        assertThat(tokenInDb.get().getTokenHash()).isNotEqualTo(rawToken);
        assertThat(tokenInDb.get().isUsed()).isFalse();
        assertThat(tokenInDb.get().isExpired()).isFalse();
        assertThat(tokenInDb.get().getUser().getId()).isEqualTo(standardUser.getId());
    }

    @Test
    @DisplayName("2. Richiesta recupero con email inesistente: risposta generica identica senza invio email")
    void forgotPassword_nonExistingAccount_returnsIdenticalGenericResponse() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("sconosciuto@example.com");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Se l'indirizzo email inserito è associato a un account, riceverai a breve un'email con le istruzioni per reimpostare la tua password."));

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("3. Richiesta recupero con account Google (senza password locale): risposta generica identica senza invio email")
    void forgotPassword_googleAccountWithoutPassword_returnsIdenticalGenericResponse() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("google.user@example.com");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Se l'indirizzo email inserito è associato a un account, riceverai a breve un'email con le istruzioni per reimpostare la tua password."));

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("4. Reimpostazione password completata: password aggiornata, token consumato e refresh token revocati")
    void resetPassword_validToken_updatesPasswordConsumesTokenAndRevokesSessions() throws Exception {
        // Crea un refresh token attivo per l'utente
        RefreshToken activeRefreshToken = new RefreshToken(standardUser, "active-refresh-token-123", Instant.now().plus(30, ChronoUnit.DAYS));
        refreshTokenRepository.save(activeRefreshToken);

        // Genera un token di reset valido
        String rawToken = "my-secret-reset-token-456";
        String tokenHash = sha256(rawToken);
        PasswordResetToken resetToken = new PasswordResetToken(standardUser, tokenHash, Instant.now().plus(30, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(resetToken);

        ResetPasswordRequest request = new ResetPasswordRequest(
                rawToken,
                "NuovaPassword123!",
                "NuovaPassword123!"
        );

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("La tua password è stata reimpostata con successo. Ora puoi effettuare il login con la nuova password."));

        // 1. Verifica che il token sia stato consumato
        PasswordResetToken consumedToken = passwordResetTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(consumedToken.isUsed()).isTrue();
        assertThat(consumedToken.getUsedAt()).isNotNull();

        // 2. Verifica che la password dell'utente sia aggiornata e compatibile con l'encoder
        User updatedUser = userRepository.findById(standardUser.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NuovaPassword123!", updatedUser.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("OldPassword123!", updatedUser.getPasswordHash())).isFalse();

        // 3. Verifica che il login con la nuova password funzioni
        LoginRequest newLogin = new LoginRequest("mario.rossi@example.com", "NuovaPassword123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // 4. Verifica che il vecchio refresh token sia stato revocato
        RefreshRequest refreshReq = new RefreshRequest("active-refresh-token-123");
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Refresh token non valido o revocato."));
    }

    @Test
    @DisplayName("5. Reimpostazione password rifiutata se il token è scaduto")
    void resetPassword_expiredToken_rejectedWithBadRequest() throws Exception {
        String rawToken = "expired-token-789";
        String tokenHash = sha256(rawToken);
        PasswordResetToken expiredToken = new PasswordResetToken(standardUser, tokenHash, Instant.now().minus(5, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(expiredToken);

        ResetPasswordRequest request = new ResetPasswordRequest(
                rawToken,
                "NuovaPassword123!",
                "NuovaPassword123!"
        );

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Il link di recupero password non è valido o è scaduto."));
    }

    @Test
    @DisplayName("6. Reimpostazione password rifiutata se il token è già stato consumato (monouso)")
    void resetPassword_alreadyUsedToken_rejectedWithBadRequest() throws Exception {
        String rawToken = "already-used-token-111";
        String tokenHash = sha256(rawToken);
        PasswordResetToken usedToken = new PasswordResetToken(standardUser, tokenHash, Instant.now().plus(20, ChronoUnit.MINUTES));
        usedToken.setUsedAt(Instant.now().minus(2, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(usedToken);

        ResetPasswordRequest request = new ResetPasswordRequest(
                rawToken,
                "NuovaPassword123!",
                "NuovaPassword123!"
        );

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Il link di recupero password non è valido o è scaduto."));
    }

    @Test
    @DisplayName("7. Reimpostazione password rifiutata se il token è inesistente")
    void resetPassword_nonExistingToken_rejectedWithBadRequest() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest(
                "non-existing-token-xyz",
                "NuovaPassword123!",
                "NuovaPassword123!"
        );

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Il link di recupero password non è valido o è scaduto."));
    }

    @Test
    @DisplayName("8. Reimpostazione password rifiutata se conferma password non corrisponde")
    void resetPassword_passwordMismatch_rejectedAndTokenNotConsumed() throws Exception {
        String rawToken = "token-for-mismatch-test";
        String tokenHash = sha256(rawToken);
        PasswordResetToken resetToken = new PasswordResetToken(standardUser, tokenHash, Instant.now().plus(30, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(resetToken);

        ResetPasswordRequest request = new ResetPasswordRequest(
                rawToken,
                "NuovaPassword123!",
                "AltraPasswordDiversa!"
        );

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("confirmPassword"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("La conferma della password non coincide con la nuova password."));

        // Il token NON deve essere stato consumato
        PasswordResetToken freshToken = passwordResetTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(freshToken.isUsed()).isFalse();
    }

    @Test
    @DisplayName("9. Brevo non configurato: richiesta recupero restituisce risposta generica senza creare token pendenti")
    void forgotPassword_whenBrevoNotConfigured_noTokenCreated() throws Exception {
        when(emailService.isConfigured()).thenReturn(false);

        ForgotPasswordRequest request = new ForgotPasswordRequest("mario.rossi@example.com");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Se l'indirizzo email inserito è associato a un account, riceverai a breve un'email con le istruzioni per reimpostare la tua password."));

        // Nessun token creato a database
        List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
        assertThat(tokens).isEmpty();
    }

    @Test
    @DisplayName("10. Errore provider durante l'invio email: token pulito per evitare token validi non recapitati")
    void forgotPassword_whenBrevoThrows_cleansUpToken() throws Exception {
        doThrow(new EmailDeliveryException("Brevo gateway timeout"))
                .when(emailService).sendPasswordResetEmail(anyString(), anyString(), anyString());

        ForgotPasswordRequest request = new ForgotPasswordRequest("mario.rossi@example.com");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Se l'indirizzo email inserito è associato a un account, riceverai a breve un'email con le istruzioni per reimpostare la tua password."));

        // Il token generato è stato rimosso in seguito al fallimento dell'invio
        List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
        assertThat(tokens).isEmpty();
    }

    @Test
    @DisplayName("11. Sicurezza e autorizzazioni: forgot-password e reset-password sono pubblici, mentre le risorse protette richiedono autenticazione")
    void publicEndpointsSecurity_accessibleWithoutAuth() throws Exception {
        // forgot-password e reset-password accessibili senza token JWT
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest("mario.rossi@example.com");
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk());

        // Verifica che un endpoint protetto richieda ancora autenticazione
        mockMvc.perform(get("/api/v1/vehicles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("12. Validazione sintassi email su forgot-password")
    void forgotPassword_invalidEmailSyntax_returnsBadRequest() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("non-una-email-valida");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    @DisplayName("13. Token monouso con richieste concorrenti: solo una richiesta ha successo e l'altra viene respinta")
    void resetPassword_concurrentRequests_onlyOneSucceeds() throws Exception {
        User concurrentUser = new User(
                UUID.randomUUID(),
                "ConcurrentUser",
                "concurrent.user@example.com",
                passwordEncoder.encode("InitPass123!")
        );
        userRepository.save(concurrentUser);

        String rawToken = "concurrent-race-token-999";
        String tokenHash = sha256(rawToken);
        PasswordResetToken resetToken = new PasswordResetToken(concurrentUser, tokenHash, Instant.now().plus(30, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(resetToken);

        ResetPasswordRequest request = new ResetPasswordRequest(
                rawToken,
                "ConcurrentPass123!",
                "ConcurrentPass123!"
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> task = () -> {
                MvcResult result = mockMvc.perform(post("/api/v1/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn();
                return result.getResponse().getStatus();
            };

            Future<Integer> f1 = executor.submit(task);
            Future<Integer> f2 = executor.submit(task);

            int status1 = f1.get();
            int status2 = f2.get();

            // Una richiesta deve avere successo (200 OK) e l'altra deve fallire con 400 Bad Request
            List<Integer> statuses = List.of(status1, status2);
            assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        } finally {
            executor.shutdown();
            passwordResetTokenRepository.deleteByUserId(concurrentUser.getId());
            userRepository.deleteById(concurrentUser.getId());
        }
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
