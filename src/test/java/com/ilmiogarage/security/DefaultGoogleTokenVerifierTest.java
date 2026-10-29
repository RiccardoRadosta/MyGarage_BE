package com.ilmiogarage.security;

import com.ilmiogarage.exception.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultGoogleTokenVerifierTest {

    @Test
    @DisplayName("Se GOOGLE_CLIENT_IDS non è configurato, la chiamata a verify lancia IllegalStateException")
    void unconfiguredVerifier_throwsIllegalStateException() {
        DefaultGoogleTokenVerifier verifier = new DefaultGoogleTokenVerifier("");

        assertThatThrownBy(() -> verifier.verify("any-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Google Sign-In non è configurato sul server");
    }

    @Test
    @DisplayName("Se idToken è nullo o vuoto, viene sollevata UnauthorizedException")
    void nullOrBlankToken_throwsUnauthorizedException() {
        DefaultGoogleTokenVerifier verifier = new DefaultGoogleTokenVerifier("dummy-client-id.apps.googleusercontent.com");

        assertThatThrownBy(() -> verifier.verify(null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Token Google mancante o vuoto");

        assertThatThrownBy(() -> verifier.verify("   "))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Token Google mancante o vuoto");
    }
}
