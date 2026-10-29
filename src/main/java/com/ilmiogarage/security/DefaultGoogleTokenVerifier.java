package com.ilmiogarage.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.ilmiogarage.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

@Component
public class DefaultGoogleTokenVerifier implements GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultGoogleTokenVerifier.class);

    private final List<String> allowedClientIds;
    private final GoogleIdTokenVerifier verifier;

    public DefaultGoogleTokenVerifier(
            @Value("${app.google.client-ids:${GOOGLE_CLIENT_IDS:${GOOGLE_CLIENT_ID:}}}") String clientIdsConfig) {

        this.allowedClientIds = parseClientIds(clientIdsConfig);

        if (!this.allowedClientIds.isEmpty()) {
            this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(this.allowedClientIds)
                    .build();
            log.info("GoogleIdTokenVerifier inizializzato con {} audience consentite: {}",
                    this.allowedClientIds.size(), this.allowedClientIds);
        } else {
            this.verifier = null;
            log.warn("Nessun Client ID Google OAuth configurato (app.google.client-ids o GOOGLE_CLIENT_IDS). " +
                    "I tentativi di autenticazione Google restituiranno un errore di configurazione.");
        }
    }

    private static List<String> parseClientIds(String str) {
        if (str == null || str.isBlank()) {
            return List.of();
        }
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    public GoogleUserInfo verify(String idTokenString) {
        if (verifier == null || allowedClientIds.isEmpty()) {
            log.error("Tentativo di verifica Google ID Token ma nessun Client ID è configurato");
            throw new IllegalStateException("Google Sign-In non è configurato sul server: nessun Client ID OAuth specificato.");
        }

        if (idTokenString == null || idTokenString.isBlank()) {
            throw new UnauthorizedException("Token Google mancante o vuoto.");
        }

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            log.warn("Verifica crittografica Google ID Token fallita: {}", e.getMessage());
            throw new UnauthorizedException("Token Google non valido o non verificabile.");
        }

        if (idToken == null) {
            log.warn("Verifica Google ID Token ha restituito null (firma non valida, token scaduto o audience non riconosciuta)");
            throw new UnauthorizedException("Token Google non valido, scaduto o audience non riconosciuta.");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();

        String sub = payload.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new UnauthorizedException("Il token Google non contiene un identificativo utente (sub) valido.");
        }

        String email = payload.getEmail();
        if (email == null || email.isBlank()) {
            throw new UnauthorizedException("Il token Google non contiene un indirizzo email.");
        }

        Boolean emailVerified = payload.getEmailVerified();
        if (emailVerified == null || !emailVerified) {
            throw new UnauthorizedException("L'indirizzo email dell'account Google non è verificato.");
        }

        String name = (String) payload.get("name");

        return new GoogleUserInfo(sub, email, true, name);
    }
}
