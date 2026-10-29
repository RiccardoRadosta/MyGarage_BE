package com.ilmiogarage.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilmiogarage.dto.auth.GoogleLoginRequest;
import com.ilmiogarage.dto.auth.RefreshRequest;
import com.ilmiogarage.dto.auth.RegisterRequest;
import com.ilmiogarage.exception.UnauthorizedException;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.repository.UserRepository;
import com.ilmiogarage.security.GoogleTokenVerifier;
import com.ilmiogarage.security.GoogleUserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GoogleAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private GoogleTokenVerifier googleTokenVerifier;

    @Test
    @DisplayName("1. Primo accesso Google: creazione nuovo utente, emissione JWT e verifica dati")
    void firstGoogleLogin_createsAccountAndIssuesJwt() throws Exception {
        String token = "valid-first-time-token";
        String googleSub = "google-sub-first-12345";
        String email = "nuovo.utente@gmail.com";
        String name = "Nuovo Utente Google";

        when(googleTokenVerifier.verify(token))
                .thenReturn(new GoogleUserInfo(googleSub, email, true, name));

        GoogleLoginRequest request = new GoogleLoginRequest(token);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.name").value(name))
                .andReturn();

        // Verifica persistenza sul database
        User createdUser = userRepository.findByGoogleSub(googleSub)
                .orElseThrow(() -> new AssertionError("L'utente con googleSub non è stato persistito"));
        assertThat(createdUser.getEmail()).isEqualTo(email);
        assertThat(createdUser.getName()).isEqualTo(name);
        assertThat(createdUser.getPasswordHash()).isNull(); // Nessun password hash per utente Google

        // Verifica che con l'access token restituito sia possibile accedere alle risorse protette
        JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = responseNode.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value(name));
    }

    @Test
    @DisplayName("2. Accesso successivo Google: identità già associata, nessun utente duplicato")
    void subsequentGoogleLogin_existingIdentity_noDuplicateUser() throws Exception {
        String googleSub = "google-sub-existing-999";
        String email = "mario.rossi@gmail.com";
        User existingUser = new User(UUID.randomUUID(), "Mario Rossi", email, null, googleSub);
        userRepository.save(existingUser);

        long initialUserCount = userRepository.count();

        String token = "valid-subsequent-token";
        when(googleTokenVerifier.verify(token))
                .thenReturn(new GoogleUserInfo(googleSub, email, true, "Mario Rossi"));

        GoogleLoginRequest request = new GoogleLoginRequest(token);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(existingUser.getId().toString()))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        // Nessun duplicato creato nel database
        assertThat(userRepository.count()).isEqualTo(initialUserCount);

        // Verifica refresh token con il token emesso
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        String refreshToken = node.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("3. Rifiuto token scaduto o malformato (401 UNAUTHORIZED)")
    void expiredOrMalformedToken_rejected() throws Exception {
        when(googleTokenVerifier.verify("expired-token"))
                .thenThrow(new UnauthorizedException("Token Google non valido, scaduto o audience non riconosciuta."));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("expired-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        when(googleTokenVerifier.verify("malformed-token"))
                .thenThrow(new UnauthorizedException("Token Google non valido o non verificabile."));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("malformed-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("4. Rifiuto firma non valida (401 UNAUTHORIZED)")
    void invalidSignatureToken_rejected() throws Exception {
        when(googleTokenVerifier.verify("invalid-signature-token"))
                .thenThrow(new UnauthorizedException("Verifica crittografica Google ID Token fallita."));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("invalid-signature-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("5. Rifiuto audience errata (401 UNAUTHORIZED)")
    void wrongAudienceToken_rejected() throws Exception {
        when(googleTokenVerifier.verify("wrong-audience-token"))
                .thenThrow(new UnauthorizedException("Token Google non valido, scaduto o audience non riconosciuta."));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("wrong-audience-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("6. Rifiuto emittente non consentito (401 UNAUTHORIZED)")
    void wrongIssuerToken_rejected() throws Exception {
        when(googleTokenVerifier.verify("wrong-issuer-token"))
                .thenThrow(new UnauthorizedException("Token Google non valido o non verificabile."));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("wrong-issuer-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("7. Prevenzione Account Takeover: email già registrata con password rifiuta il login Google automatico (409 CONFLICT)")
    void accountTakeoverPrevention_emailExistsWithPassword_rejected() throws Exception {
        // Registra utente con email e password standard
        RegisterRequest registerReq = new RegisterRequest("Utente Password", "giacomo@example.com", "PasswordSicura123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        // Tentativo di Google login con la stessa email ma sub diverso / non associato
        String token = "takeover-attempt-token";
        when(googleTokenVerifier.verify(token))
                .thenReturn(new GoogleUserInfo("attacker-sub-777", "giacomo@example.com", true, "Giacomo Attacker"));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest(token))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("8. Email non verificata da Google viene rifiutata (401 UNAUTHORIZED)")
    void unverifiedGoogleEmail_rejected() throws Exception {
        String token = "unverified-email-token";
        when(googleTokenVerifier.verify(token))
                .thenReturn(new GoogleUserInfo("sub-unverified", "unverified@example.com", false, "Unverified User"));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest(token))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("9. Richiesta senza idToken o con idToken vuoto viene validata e rifiutata (400 VALIDATION_ERROR)")
    void blankIdToken_rejectedWithValidationError() throws Exception {
        GoogleLoginRequest emptyRequest = new GoogleLoginRequest("   ");

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("idToken"));
    }
}
