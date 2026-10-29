package com.ilmiogarage.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilmiogarage.dto.auth.LoginRequest;
import com.ilmiogarage.dto.auth.RegisterRequest;
import com.ilmiogarage.exception.EmailDeliveryException;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.repository.UserRepository;
import com.ilmiogarage.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WelcomeEmailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private EmailService emailService;

    @Test
    @DisplayName("Registrazione completata con successo: invio email di benvenuto richiesto esattamente una volta")
    void registerSuccess_sendsWelcomeEmailOnce() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Luigi Verdi",
                "luigi.verdi@example.com",
                "Password123!"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("luigi.verdi@example.com"));

        // Verifica che l'invio dell'email sia stato invocato una sola volta con i dati corretti
        verify(emailService, times(1)).sendWelcomeEmail("luigi.verdi@example.com", "Luigi Verdi");
    }

    @Test
    @DisplayName("Errore del provider email Brevo non compromette la registrazione dell'utente")
    void register_whenBrevoThrows_registrationStillSucceeds() throws Exception {
        doThrow(new EmailDeliveryException("Brevo API timeout"))
                .when(emailService).sendWelcomeEmail(anyString(), anyString());

        RegisterRequest request = new RegisterRequest(
                "Marco Neri",
                "marco.neri@example.com",
                "Password123!"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("marco.neri@example.com"));

        // L'utente risulta comunque registrato a database
        Optional<User> savedUser = userRepository.findByEmail("marco.neri@example.com");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getName()).isEqualTo("Marco Neri");
    }

    @Test
    @DisplayName("Login ordinario non invia alcuna email di benvenuto")
    void normalLogin_doesNotSendWelcomeEmail() throws Exception {
        // Registra prima l'utente
        RegisterRequest registerReq = new RegisterRequest("Sara Bianchi", "sara.bianchi@example.com", "Password123!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        clearInvocations(emailService);

        // Esegue il login
        LoginRequest loginReq = new LoginRequest("sara.bianchi@example.com", "Password123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // Nessuna email deve essere inviata durante il login
        verifyNoInteractions(emailService);
    }
}
