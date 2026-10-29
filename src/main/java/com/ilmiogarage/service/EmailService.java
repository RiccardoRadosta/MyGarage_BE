package com.ilmiogarage.service;

public interface EmailService {

    /**
     * Verifica se il provider email è configurato con credenziali valide.
     */
    boolean isConfigured();

    /**
     * Invia un'email di benvenuto all'utente appena registrato.
     *
     * @param toEmail indirizzo email del destinatario
     * @param toName nome del destinatario
     */
    void sendWelcomeEmail(String toEmail, String toName);

    /**
     * Invia un'email contenente il link con token monouso per il recupero password.
     *
     * @param toEmail indirizzo email del destinatario
     * @param toName nome del destinatario
     * @param rawToken token crittografico in chiaro (non censurato) per la costruzione del link
     */
    void sendPasswordResetEmail(String toEmail, String toName, String rawToken);
}
