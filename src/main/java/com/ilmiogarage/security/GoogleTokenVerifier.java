package com.ilmiogarage.security;

public interface GoogleTokenVerifier {

    /**
     * Verifica crittograficamente la firma, l'emittente (accounts.google.com),
     * l'audience (Client ID) e la scadenza dell'ID Token Google.
     *
     * @param idTokenString ID Token JWT emesso da Google
     * @return GoogleUserInfo contenente i claim convalidati (sub, email, email_verified, name)
     */
    GoogleUserInfo verify(String idTokenString);
}
