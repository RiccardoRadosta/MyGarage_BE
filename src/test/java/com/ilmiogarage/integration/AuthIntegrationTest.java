package com.ilmiogarage.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilmiogarage.dto.auth.LoginRequest;
import com.ilmiogarage.dto.auth.LogoutRequest;
import com.ilmiogarage.dto.auth.RefreshRequest;
import com.ilmiogarage.dto.auth.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Registrazione con successo, login, refresh e logout")
    void fullAuthFlow_success() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Test User", "testflow@example.com", "Password123");

        // 1. Registrazione
        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.user.email").value("testflow@example.com"))
                .andReturn();

        JsonNode regNode = objectMapper.readTree(regResult.getResponse().getContentAsString());
        String refreshToken = regNode.get("refreshToken").asText();

        // 2. Login
        LoginRequest loginReq = new LoginRequest("testflow@example.com", "Password123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.name").value("Test User"))
                .andReturn();

        JsonNode loginNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String loginAccessToken = loginNode.get("accessToken").asText();

        // 3. Accesso a risorsa protetta con access token
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + loginAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("testflow@example.com"));

        // 4. Refresh token
        RefreshRequest refreshReq = new RefreshRequest(refreshToken);
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user").doesNotExist()) // prompt: "Questa risposta non include user"
                .andReturn();

        JsonNode refreshNode = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String rotatedRefreshToken = refreshNode.get("refreshToken").asText();
        assertThat(rotatedRefreshToken).isNotEqualTo(refreshToken);

        // 5. Logout
        LogoutRequest logoutReq = new LogoutRequest(rotatedRefreshToken);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + loginAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isNoContent());

        // 6. Refresh con token revocato fallisce
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(rotatedRefreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Caso 2: Registrazione con email duplicata fallisce con 409 EMAIL_ALREADY_EXISTS")
    void register_duplicateEmail_failsWith409() throws Exception {
        RegisterRequest initial = new RegisterRequest("Primo Utente", "duplicate@example.com", "Password123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initial)))
                .andExpect(status().isCreated());

        // Tentativo con stessa email (anche con maiuscole per testare case-insensitivity)
        RegisterRequest duplicate = new RegisterRequest("Secondo Utente", "DUPLICATE@example.com", "Password123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("L'indirizzo email 'duplicate@example.com' è già registrato."))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("Login con credenziali errate restituisce 401 con messaggio generico")
    void login_invalidCredentials_returns401Generic() throws Exception {
        LoginRequest wrongPass = new LoginRequest("nonexistent@example.com", "WrongPassword");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPass)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.detail").value("Credenziali non valide."));
    }

    @Test
    @DisplayName("Accesso a endpoint protetto senza token restituisce 401")
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
