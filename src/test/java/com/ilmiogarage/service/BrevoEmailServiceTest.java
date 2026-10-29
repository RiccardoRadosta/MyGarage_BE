package com.ilmiogarage.service;

import com.ilmiogarage.exception.EmailDeliveryException;
import com.ilmiogarage.exception.EmailServiceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BrevoEmailServiceTest {

    @Test
    @DisplayName("isConfigured restituisce false se apiKey è null o vuota")
    void isConfigured_returnsFalseWhenApiKeyEmpty() {
        BrevoEmailService unconfigured = new BrevoEmailService(
                "", "no-reply@ilmiogarage.com", "Il Mio Garage",
                "https://ilmiogarage.com", 30, RestClient.builder().build()
        );
        assertThat(unconfigured.isConfigured()).isFalse();

        BrevoEmailService nullKey = new BrevoEmailService(
                null, "no-reply@ilmiogarage.com", "Il Mio Garage",
                "https://ilmiogarage.com", 30, RestClient.builder().build()
        );
        assertThat(nullKey.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured restituisce true se apiKey è presente")
    void isConfigured_returnsTrueWhenApiKeyPresent() {
        BrevoEmailService configured = new BrevoEmailService(
                "test-brevo-api-key", "no-reply@ilmiogarage.com", "Il Mio Garage",
                "https://ilmiogarage.com", 30, RestClient.builder().build()
        );
        assertThat(configured.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("sendWelcomeEmail con Brevo non configurato non lancia eccezioni")
    void sendWelcomeEmail_whenNotConfigured_doesNotThrow() {
        BrevoEmailService unconfigured = new BrevoEmailService(
                "", "no-reply@ilmiogarage.com", "Il Mio Garage",
                "https://ilmiogarage.com", 30, RestClient.builder().build()
        );

        assertThatCode(() -> unconfigured.sendWelcomeEmail("mario.rossi@example.com", "Mario Rossi"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendPasswordResetEmail con Brevo non configurato solleva EmailServiceUnavailableException")
    void sendPasswordResetEmail_whenNotConfigured_throwsException() {
        BrevoEmailService unconfigured = new BrevoEmailService(
                "", "no-reply@ilmiogarage.com", "Il Mio Garage",
                "https://ilmiogarage.com", 30, RestClient.builder().build()
        );

        assertThatThrownBy(() -> unconfigured.sendPasswordResetEmail("mario.rossi@example.com", "Mario Rossi", "raw-token"))
                .isInstanceOf(EmailServiceUnavailableException.class)
                .hasMessageContaining("non è configurato");
    }

    @Test
    @DisplayName("sendWelcomeEmail configurato invia correttamente la richiesta HTTP con header e payload")
    void sendWelcomeEmail_configured_sendsCorrectRequest() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.brevo.com/v3");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        BrevoEmailService service = new BrevoEmailService(
                "my-brevo-key", "no-reply@ilmiogarage.com", "Il Mio Garage",
                "https://ilmiogarage.com", 30, builder.build()
        );

        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "my-brevo-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sender.name").value("Il Mio Garage"))
                .andExpect(jsonPath("$.sender.email").value("no-reply@ilmiogarage.com"))
                .andExpect(jsonPath("$.to[0].email").value("mario@example.com"))
                .andExpect(jsonPath("$.to[0].name").value("Mario Rossi"))
                .andExpect(jsonPath("$.subject").value("Benvenuto su Il Mio Garage!"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"messageId\":\"msg-123\"}"));

        service.sendWelcomeEmail("mario@example.com", "Mario Rossi");

        server.verify();
    }

    @Test
    @DisplayName("sendPasswordResetEmail configurato include il link con il token e risponde con successo")
    void sendPasswordResetEmail_configured_sendsCorrectRequest() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.brevo.com/v3");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        BrevoEmailService service = new BrevoEmailService(
                "my-brevo-key", "no-reply@ilmiogarage.com", "Il Mio Garage",
                "https://ilmiogarage.com", 30, builder.build()
        );

        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "my-brevo-key"))
                .andExpect(jsonPath("$.to[0].email").value("mario@example.com"))
                .andExpect(jsonPath("$.subject").value("Recupero password — Il Mio Garage"))
                .andExpect(jsonPath("$.textContent").value(org.hamcrest.Matchers.containsString("https://ilmiogarage.com/reset-password?token=secret123")))
                .andExpect(jsonPath("$.textContent").value(org.hamcrest.Matchers.containsString("secret123")))
                .andExpect(jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString("secret123")))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"messageId\":\"msg-456\"}"));

        service.sendPasswordResetEmail("mario@example.com", "Mario Rossi", "secret123");

        server.verify();
    }

    @Test
    @DisplayName("sendPasswordResetEmail con baseUrl Expo Go genera il link corretto exp://.../--/reset-password sia in text che html")
    void sendPasswordResetEmail_expoGoUrl_sendsCorrectLinkInTextAndHtml() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.brevo.com/v3");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        BrevoEmailService service = new BrevoEmailService(
                "my-brevo-key", "no-reply@ilmiogarage.com", "Il Mio Garage",
                "exp://192.168.0.172:8081", 30, builder.build()
        );

        String expectedLink = "exp://192.168.0.172:8081/--/reset-password?token=secret123";

        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "my-brevo-key"))
                .andExpect(jsonPath("$.to[0].email").value("mario@example.com"))
                .andExpect(jsonPath("$.subject").value("Recupero password — Il Mio Garage"))
                .andExpect(jsonPath("$.textContent").value(org.hamcrest.Matchers.containsString(expectedLink)))
                .andExpect(jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString("href=\"" + expectedLink + "\"")))
                .andExpect(jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString(expectedLink)))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"messageId\":\"msg-789\"}"));

        service.sendPasswordResetEmail("mario@example.com", "Mario Rossi", "secret123");

        server.verify();
    }

    @Test
    @DisplayName("buildPasswordResetLink genera correttamente deep link Expo Go, custom scheme e HTTPS")
    void buildPasswordResetLink_generatesCorrectUrls() {
        BrevoEmailService service = new BrevoEmailService(
                "my-brevo-key", "no-reply@ilmiogarage.com", "Il Mio Garage",
                "https://ilmiogarage.com", 30, RestClient.builder().build()
        );

        // Expo Go con schema exp://
        assertThat(service.buildPasswordResetLink("exp://192.168.0.172:8081", "test-token-123"))
                .isEqualTo("exp://192.168.0.172:8081/--/reset-password?token=test-token-123");

        // Expo Go con prefisso /-- già specificato
        assertThat(service.buildPasswordResetLink("exp://192.168.0.172:8081/--", "test-token-123"))
                .isEqualTo("exp://192.168.0.172:8081/--/reset-password?token=test-token-123");

        // Metro bundler HTTP porta 8081 convertito in exp:// con /--/
        assertThat(service.buildPasswordResetLink("http://192.168.0.172:8081", "test-token-123"))
                .isEqualTo("exp://192.168.0.172:8081/--/reset-password?token=test-token-123");

        // Schema applicativo standalone/APK
        assertThat(service.buildPasswordResetLink("mygarage://", "test-token-123"))
                .isEqualTo("mygarage://reset-password?token=test-token-123");

        // Dominio standard web HTTPS
        assertThat(service.buildPasswordResetLink("https://ilmiogarage.com", "test-token-123"))
                .isEqualTo("https://ilmiogarage.com/reset-password?token=test-token-123");
    }

    @Test
    @DisplayName("sendPasswordResetEmail con errore HTTP da Brevo solleva EmailDeliveryException controllata")
    void sendPasswordResetEmail_brevoError_throwsEmailDeliveryException() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.brevo.com/v3");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        BrevoEmailService service = new BrevoEmailService(
                "my-brevo-key", "no-reply@ilmiogarage.com", "Il Mio Garage",
                "https://ilmiogarage.com", 30, builder.build()
        );

        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withServerError().body("{\"code\":\"server_error\",\"message\":\"Internal error\"}"));

        assertThatThrownBy(() -> service.sendPasswordResetEmail("mario@example.com", "Mario Rossi", "secret123"))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("Errore restituito dal provider email");

        server.verify();
    }
}
