package com.ilmiogarage.service;

import com.ilmiogarage.dto.email.BrevoEmailPayload;
import com.ilmiogarage.dto.email.BrevoRecipient;
import com.ilmiogarage.dto.email.BrevoSender;
import com.ilmiogarage.exception.EmailDeliveryException;
import com.ilmiogarage.exception.EmailServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

@Service
public class BrevoEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailService.class);

    private final String apiKey;
    private final String senderEmail;
    private final String senderName;
    private final String publicBaseUrl;
    private final int expirationMinutes;
    private final RestClient restClient;

    @Autowired
    public BrevoEmailService(
            @Value("${app.brevo.api-key:}") String apiKey,
            @Value("${app.brevo.sender-email:no-reply@ilmiogarage.com}") String senderEmail,
            @Value("${app.brevo.sender-name:Il Mio Garage}") String senderName,
            @Value("${app.brevo.base-url:https://api.brevo.com/v3}") String baseUrl,
            @Value("${app.brevo.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${app.brevo.read-timeout-seconds:10}") int readTimeoutSeconds,
            @Value("${app.public-base-url:https://ilmiogarage.com}") String publicBaseUrl,
            @Value("${app.password-reset.expiration-minutes:30}") int expirationMinutes) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.senderEmail = senderEmail != null && !senderEmail.isBlank() ? senderEmail.trim() : "no-reply@ilmiogarage.com";
        this.senderName = senderName != null && !senderName.isBlank() ? senderName.trim() : "Il Mio Garage";
        this.publicBaseUrl = publicBaseUrl != null && !publicBaseUrl.isBlank() ? publicBaseUrl.trim() : "https://ilmiogarage.com";
        this.expirationMinutes = expirationMinutes > 0 ? expirationMinutes : 30;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "https://api.brevo.com/v3")
                .requestFactory(requestFactory)
                .build();

        if (isConfigured()) {
            log.info("BrevoEmailService inizializzato con successo. Mittente: {} <{}>, publicBaseUrl: {}",
                    this.senderName, this.senderEmail, this.publicBaseUrl);
        } else {
            log.warn("BrevoEmailService inizializzato SENZA API KEY. L'invio delle email è disabilitato (BREVO_API_KEY non configurata).");
        }
    }

    // Costruttore package-private per test con RestClient mockato
    BrevoEmailService(
            String apiKey,
            String senderEmail,
            String senderName,
            String publicBaseUrl,
            int expirationMinutes,
            RestClient restClient) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.senderEmail = senderEmail;
        this.senderName = senderName;
        this.publicBaseUrl = publicBaseUrl;
        this.expirationMinutes = expirationMinutes;
        this.restClient = restClient;
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public void sendWelcomeEmail(String toEmail, String toName) {
        if (!isConfigured()) {
            log.warn("Invio email di benvenuto ignorato: Brevo non configurato");
            return;
        }

        String displayName = (toName != null && !toName.isBlank()) ? toName.trim() : "Utente";
        String subject = "Benvenuto su Il Mio Garage!";
        String textContent = buildWelcomeText(displayName);
        String htmlContent = buildWelcomeHtml(displayName);

        sendBrevoEmail(toEmail, displayName, subject, htmlContent, textContent);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String toName, String rawToken) {
        if (!isConfigured()) {
            log.error("Tentativo di invio email di recupero password fallito: Brevo non configurato");
            throw new EmailServiceUnavailableException("Il servizio di invio email non è configurato.");
        }

        String displayName = (toName != null && !toName.isBlank()) ? toName.trim() : "Utente";
        String resetLink = buildPasswordResetLink(publicBaseUrl, rawToken);

        String subject = "Recupero password — Il Mio Garage";
        String textContent = buildPasswordResetText(displayName, resetLink, rawToken);
        String htmlContent = buildPasswordResetHtml(displayName, resetLink, rawToken);

        sendBrevoEmail(toEmail, displayName, subject, htmlContent, textContent);
    }

    String buildPasswordResetLink(String baseUrl, String token) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://ilmiogarage.com/reset-password?token=" + token;
        }

        String clean = baseUrl.trim();

        // 1. Schema mobile puro senza host (es. "mygarage://")
        if (clean.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://$")) {
            return clean + "reset-password?token=" + token;
        }

        // Rimuove eventuali slash finali per normalizzare
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }

        // 2. URL per Expo Go (schema exp:// o server Metro su porta 8081)
        if (clean.startsWith("exp://") || clean.matches("^https?://.*:8081(/.*)?$")) {
            if (clean.startsWith("http://")) {
                clean = "exp://" + clean.substring("http://".length());
            } else if (clean.startsWith("https://")) {
                clean = "exp://" + clean.substring("https://".length());
            }

            if (clean.endsWith("/--")) {
                return clean + "/reset-password?token=" + token;
            } else {
                return clean + "/--/reset-password?token=" + token;
            }
        }

        // 3. Schema custom con host (es. "mygarage://app") o standard web (https://ilmiogarage.com)
        return clean + "/reset-password?token=" + token;
    }

    private void sendBrevoEmail(String toEmail, String toName, String subject, String htmlContent, String textContent) {
        BrevoSender sender = new BrevoSender(senderName, senderEmail);
        BrevoRecipient recipient = new BrevoRecipient(toName, toEmail);
        BrevoEmailPayload payload = new BrevoEmailPayload(
                sender,
                List.of(recipient),
                subject,
                htmlContent,
                textContent
        );

        try {
            restClient.post()
                    .uri("/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Email inviata con successo via Brevo [subject: {}]", subject);
        } catch (RestClientResponseException ex) {
            log.error("Errore HTTP da Brevo: status={}", ex.getStatusCode().value());
            throw new EmailDeliveryException("Errore restituito dal provider email (status " + ex.getStatusCode().value() + ")", ex);
        } catch (ResourceAccessException ex) {
            log.error("Timeout o provider email non raggiungibile: {}", ex.getMessage());
            throw new EmailDeliveryException("Timeout o impossibilità di raggiungere il servizio email", ex);
        } catch (Exception ex) {
            log.error("Errore imprevisto durante l'invio dell'email: {}", ex.getMessage());
            throw new EmailDeliveryException("Errore durante l'invio dell'email", ex);
        }
    }

    private String buildWelcomeText(String name) {
        return "Ciao " + name + ",\n\n" +
                "Grazie per esserti registrato a Il Mio Garage!\n\n" +
                "Il tuo account è ora attivo. Con Il Mio Garage puoi:\n" +
                "- Gestire tutti i tuoi veicoli (auto, moto, scooter, furgoni);\n" +
                "- Registrare gli interventi di manutenzione svolti;\n" +
                "- Monitorare le spese e il chilometraggio nel tempo;\n" +
                "- Salvare ricevute e documenti in formato PDF o foto.\n\n" +
                "Accedi subito all'app per aggiungere il tuo primo veicolo.\n\n" +
                "A presto,\n" +
                "Il team di Il Mio Garage";
    }

    private String buildWelcomeHtml(String name) {
        return "<!DOCTYPE html>" +
                "<html><head><meta charset=\"UTF-8\">" +
                "<style>" +
                "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f4f5f7; margin: 0; padding: 20px; color: #1e293b; }" +
                ".container { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }" +
                ".header { background-color: #0f172a; padding: 24px; text-align: center; color: #ffffff; }" +
                ".header h1 { margin: 0; font-size: 22px; font-weight: 700; letter-spacing: -0.5px; }" +
                ".content { padding: 32px 24px; line-height: 1.6; font-size: 15px; }" +
                ".features { background-color: #f8fafc; border-left: 4px solid #0284c7; padding: 16px 20px; margin: 20px 0; border-radius: 0 4px 4px 0; }" +
                ".features ul { margin: 0; padding-left: 20px; }" +
                ".footer { background-color: #f8fafc; padding: 16px 24px; text-align: center; font-size: 12px; color: #64748b; border-top: 1px solid #e2e8f0; }" +
                "</style></head><body>" +
                "<div class=\"container\">" +
                "  <div class=\"header\"><h1>Il Mio Garage</h1></div>" +
                "  <div class=\"content\">" +
                "    <p>Ciao <strong>" + escapeHtml(name) + "</strong>,</p>" +
                "    <p>Grazie per esserti registrato a <strong>Il Mio Garage</strong>!</p>" +
                "    <p>Il tuo account è ora attivo e pronto all'uso.</p>" +
                "    <div class=\"features\">" +
                "      <p style=\"margin-top:0; font-weight:600;\">Cosa puoi fare con l'app:</p>" +
                "      <ul>" +
                "        <li>Aggiungere e gestire auto, moto, furgoni e altri veicoli</li>" +
                "        <li>Registrare gli interventi di manutenzione svolti</li>" +
                "        <li>Monitorare i costi complessivi e il chilometraggio</li>" +
                "        <li>Allegare fatture, ricevute o foto alle tue manutenzioni</li>" +
                "      </ul>" +
                "    </div>" +
                "    <p>Apri l'app sul tuo dispositivo e inizia subito ad aggiungere i tuoi veicoli!</p>" +
                "    <p style=\"margin-top: 24px;\">A presto,<br><strong>Il team di Il Mio Garage</strong></p>" +
                "  </div>" +
                "  <div class=\"footer\">Questa è un'email automatica inviata da Il Mio Garage in seguito alla creazione del tuo account.</div>" +
                "</div></body></html>";
    }

    private String buildPasswordResetText(String name, String resetLink, String rawToken) {
        return "Ciao " + name + ",\n\n" +
                "Abbiamo ricevuto una richiesta di reimpostazione della password per il tuo account Il Mio Garage.\n\n" +
                "Per scegliere una nuova password, puoi utilizzare direttamente il seguente link (valido per " + expirationMinutes + " minuti):\n" +
                resetLink + "\n\n" +
                "--- CODICE DI RECUPERO MANUALE ---\n" +
                rawToken + "\n\n" +
                "Se il link non si apre direttamente nell'app (ad es. durante lo sviluppo con Expo Go):\n" +
                "1. Apri l'app Il Mio Garage sul tuo dispositivo;\n" +
                "2. Nella schermata di accesso tocca 'Password dimenticata?';\n" +
                "3. Incolla il codice di recupero sopra e inserisci la nuova password.\n\n" +
                "Se non hai richiesto tu il recupero della password, puoi tranquillamente ignorare questa email: la tua password attuale rimarrà invariata e il tuo account protetto.\n\n" +
                "A presto,\n" +
                "Il team di Il Mio Garage";
    }

    private String buildPasswordResetHtml(String name, String resetLink, String rawToken) {
        return "<!DOCTYPE html>" +
                "<html><head><meta charset=\"UTF-8\">" +
                "<style>" +
                "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f4f5f7; margin: 0; padding: 20px; color: #1e293b; }" +
                ".container { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }" +
                ".header { background-color: #0f172a; padding: 24px; text-align: center; color: #ffffff; }" +
                ".header h1 { margin: 0; font-size: 22px; font-weight: 700; letter-spacing: -0.5px; }" +
                ".content { padding: 32px 24px; line-height: 1.6; font-size: 15px; }" +
                ".btn-container { text-align: center; margin: 28px 0; }" +
                ".btn { background-color: #0284c7; color: #ffffff !important; text-decoration: none; padding: 14px 28px; border-radius: 6px; font-weight: 600; display: inline-block; }" +
                ".token-box { background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 16px; margin: 24px 0; text-align: center; }" +
                ".token-label { font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; color: #64748b; margin-bottom: 6px; }" +
                ".token-value { font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace; font-size: 15px; font-weight: 700; color: #0f172a; word-break: break-all; }" +
                ".token-hint { font-size: 13px; color: #64748b; margin-top: 8px; margin-bottom: 0; }" +
                ".warning { background-color: #fffbeb; border-left: 4px solid #f59e0b; padding: 12px 16px; margin: 24px 0; font-size: 13px; color: #92400e; }" +
                ".link-fallback { word-break: break-all; font-size: 12px; color: #64748b; margin-top: 20px; }" +
                ".footer { background-color: #f8fafc; padding: 16px 24px; text-align: center; font-size: 12px; color: #64748b; border-top: 1px solid #e2e8f0; }" +
                "</style></head><body>" +
                "<div class=\"container\">" +
                "  <div class=\"header\"><h1>Il Mio Garage</h1></div>" +
                "  <div class=\"content\">" +
                "    <p>Ciao <strong>" + escapeHtml(name) + "</strong>,</p>" +
                "    <p>Abbiamo ricevuto una richiesta di reimpostazione della password per il tuo account.</p>" +
                "    <div class=\"btn-container\">" +
                "      <a href=\"" + escapeHtml(resetLink) + "\" class=\"btn\" target=\"_blank\">Reimposta Password</a>" +
                "    </div>" +
                "    <div class=\"token-box\">" +
                "      <div class=\"token-label\">Codice di recupero manuale</div>" +
                "      <div class=\"token-value\">" + escapeHtml(rawToken) + "</div>" +
                "      <p class=\"token-hint\">Se il pulsante non apre direttamente l'app (es. durante lo sviluppo con Expo Go), apri l'app Il Mio Garage, seleziona <em>Reimposta password</em> e incolla questo codice.</p>" +
                "    </div>" +
                "    <div class=\"warning\">" +
                "      Il link e il codice sono validi per <strong>" + expirationMinutes + " minuti</strong> ed è monouso.<br>" +
                "      Se non hai richiesto tu il recupero della password, ignora questa email: il tuo account rimarrà al sicuro." +
                "    </div>" +
                "    <div class=\"link-fallback\">" +
                "      Collegamento diretto:<br>" +
                "      <a href=\"" + escapeHtml(resetLink) + "\" style=\"color: #0284c7;\">" + escapeHtml(resetLink) + "</a>" +
                "    </div>" +
                "    <p style=\"margin-top: 24px;\">A presto,<br><strong>Il team di Il Mio Garage</strong></p>" +
                "  </div>" +
                "  <div class=\"footer\">Questa è un'email automatica di sicurezza inviata da Il Mio Garage.</div>" +
                "</div></body></html>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
