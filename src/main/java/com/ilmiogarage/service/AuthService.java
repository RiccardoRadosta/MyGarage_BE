package com.ilmiogarage.service;

import com.ilmiogarage.dto.auth.AuthResponse;
import com.ilmiogarage.dto.auth.ForgotPasswordRequest;
import com.ilmiogarage.dto.auth.GoogleLoginRequest;
import com.ilmiogarage.dto.auth.LoginRequest;
import com.ilmiogarage.dto.auth.LogoutRequest;
import com.ilmiogarage.dto.auth.RefreshRequest;
import com.ilmiogarage.dto.auth.RegisterRequest;
import com.ilmiogarage.dto.auth.ResetPasswordRequest;
import com.ilmiogarage.dto.auth.TokenRefreshResponse;
import com.ilmiogarage.dto.common.MessageResponse;
import com.ilmiogarage.dto.user.UserResponse;
import com.ilmiogarage.exception.EmailAlreadyExistsException;
import com.ilmiogarage.exception.UnauthorizedException;
import com.ilmiogarage.exception.ValidationException;
import com.ilmiogarage.mapper.UserMapper;
import com.ilmiogarage.model.entity.PasswordResetToken;
import com.ilmiogarage.model.entity.RefreshToken;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.repository.PasswordResetTokenRepository;
import com.ilmiogarage.repository.RefreshTokenRepository;
import com.ilmiogarage.repository.UserRepository;
import com.ilmiogarage.security.GoogleTokenVerifier;
import com.ilmiogarage.security.GoogleUserInfo;
import com.ilmiogarage.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final UserMapper userMapper;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final int resetExpirationMinutes;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            UserMapper userMapper,
            GoogleTokenVerifier googleTokenVerifier,
            @Value("${app.password-reset.expiration-minutes:30}") int resetExpirationMinutes) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.userMapper = userMapper;
        this.googleTokenVerifier = googleTokenVerifier;
        this.resetExpirationMinutes = resetExpirationMinutes > 0 ? resetExpirationMinutes : 30;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration failed: email already exists [{}]", normalizedEmail);
            throw new EmailAlreadyExistsException("L'indirizzo email '" + normalizedEmail + "' è già registrato.");
        }

        User user = new User(
                UUID.randomUUID(),
                request.name().trim(),
                normalizedEmail,
                passwordEncoder.encode(request.password())
        );

        User savedUser = userRepository.save(user);
        log.info("Registered new user with id: {}", savedUser.getId());

        AuthResponse response = issueAuthTokens(savedUser);

        try {
            emailService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getName());
        } catch (Exception ex) {
            log.error("Impossibile inviare l'email di benvenuto all'utente [{}]: {}", savedUser.getId(), ex.getMessage());
        }

        return response;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("Login failed: user not found [{}]", normalizedEmail);
                    return new UnauthorizedException("Credenziali non valide.");
                });

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed: invalid password for user [{}]", normalizedEmail);
            throw new UnauthorizedException("Credenziali non valide.");
        }

        log.info("User {} logged in successfully via password", user.getId());
        return issueAuthTokens(user);
    }

    @Transactional
    public AuthResponse authenticateWithGoogle(GoogleLoginRequest request) {
        GoogleUserInfo googleUser = googleTokenVerifier.verify(request.idToken());

        if (!googleUser.emailVerified()) {
            log.warn("Google authentication rejected: email {} is not verified by Google", googleUser.email());
            throw new UnauthorizedException("L'indirizzo email dell'account Google non è verificato.");
        }

        String googleSub = googleUser.sub();
        String normalizedEmail = googleUser.email().trim().toLowerCase();

        // 1. Identifica utente per claim 'sub' stabile
        Optional<User> userBySub = userRepository.findByGoogleSub(googleSub);
        if (userBySub.isPresent()) {
            User existingGoogleUser = userBySub.get();
            log.info("Google Sign-In successful for existing linked user: {}", existingGoogleUser.getId());
            return issueAuthTokens(existingGoogleUser);
        }

        // 2. Controllo prevenzione account takeover: se l'email esiste già registrata con password standard, non collegare automaticamente
        Optional<User> userByEmail = userRepository.findByEmail(normalizedEmail);
        if (userByEmail.isPresent()) {
            log.warn("Google Sign-In collision: email [{}] exists with different identity (sub: {})", normalizedEmail, googleSub);
            throw new EmailAlreadyExistsException(
                    "L'indirizzo email '" + normalizedEmail + "' è già registrato con email e password. " +
                    "Accedi con le tue credenziali standard per proteggere il tuo account.");
        }

        // 3. Creazione nuovo account utente per la nuova identità Google
        String displayName = (googleUser.name() != null && !googleUser.name().isBlank())
                ? googleUser.name().trim()
                : normalizedEmail.split("@")[0];

        if (displayName.length() > 100) {
            displayName = displayName.substring(0, 100);
        }

        User newUser = new User(
                UUID.randomUUID(),
                displayName,
                normalizedEmail,
                null, // Nessun password_hash per utenti creati con Google
                googleSub
        );

        User savedUser = userRepository.save(newUser);
        log.info("Registered new user via Google Sign-In with id: {} (sub: {})", savedUser.getId(), googleSub);

        return issueAuthTokens(savedUser);
    }

    @Transactional
    public TokenRefreshResponse refresh(RefreshRequest request) {
        RefreshToken currentToken = refreshTokenRepository.findByTokenAndRevokedFalse(request.refreshToken())
                .orElseThrow(() -> new UnauthorizedException("Refresh token non valido o revocato."));

        if (currentToken.isExpired()) {
            currentToken.setRevoked(true);
            refreshTokenRepository.save(currentToken);
            throw new UnauthorizedException("Refresh token scaduto.");
        }

        // Revoke current token (rotation)
        currentToken.setRevoked(true);
        refreshTokenRepository.save(currentToken);

        User user = currentToken.getUser();
        String newAccessToken = tokenProvider.generateAccessToken(user.getId());
        RefreshToken newRefreshToken = createRefreshToken(user);

        log.info("Rotated refresh token for user: {}", user.getId());
        return TokenRefreshResponse.of(newAccessToken, newRefreshToken.getToken(), tokenProvider.getExpirationSeconds());
    }

    @Transactional
    public void logout(LogoutRequest request, UUID currentUserId) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByToken(request.refreshToken());
            tokenOpt.ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }
        log.info("User {} logged out successfully", currentUserId);
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        MessageResponse defaultResponse = new MessageResponse(
                "Se l'indirizzo email inserito è associato a un account, riceverai a breve un'email con le istruzioni per reimpostare la tua password."
        );

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for non-existing email");
            return defaultResponse;
        }

        User user = userOpt.get();

        // Se l'account è solo Google (password_hash == null), non consentire reset password locale
        if (user.getPasswordHash() == null) {
            log.warn("Password reset ignored for Google-only account (id: {})", user.getId());
            return defaultResponse;
        }

        // Se il provider email non è configurato, registriamo l'errore e non creiamo token pendenti
        if (!emailService.isConfigured()) {
            log.error("Tentativo di invio reset password ma il provider email non è configurato");
            return defaultResponse;
        }

        // Rimuove eventuali token precedenti per questo utente
        passwordResetTokenRepository.deleteByUserId(user.getId());

        // Genera token crittograficamente sicuro
        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(resetExpirationMinutes, ChronoUnit.MINUTES);

        PasswordResetToken resetToken = new PasswordResetToken(user, tokenHash, expiresAt);
        passwordResetTokenRepository.save(resetToken);

        try {
            emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), rawToken);
            log.info("Password reset email sent to user: {}", user.getId());
        } catch (Exception ex) {
            log.error("Errore durante l'invio dell'email di reset password per l'utente {}: {}", user.getId(), ex.getMessage());
            // Pulizia immediata del token per evitare token validi senza che l'email sia stata recapitata
            passwordResetTokenRepository.delete(resetToken);
        }

        return defaultResponse;
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ValidationException("confirmPassword", "Mismatch", "La conferma della password non coincide con la nuova password.");
        }

        String rawToken = request.token().trim();
        String tokenHash = hashToken(rawToken);

        // Blocco pessimistico (SELECT ... FOR UPDATE) per garantire atomicità tra richieste concorrenti
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHashWithLock(tokenHash)
                .orElseThrow(() -> new ValidationException("token", "InvalidToken", "Il link di recupero password non è valido o è scaduto."));

        if (resetToken.isUsed() || resetToken.isExpired()) {
            log.warn("Password reset rejected: token is used={} or expired={}", resetToken.isUsed(), resetToken.isExpired());
            throw new ValidationException("token", "InvalidToken", "Il link di recupero password non è valido o è scaduto.");
        }

        // Consuma il token in modo atomico
        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        // Aggiorna la password dell'utente con lo stesso password encoder
        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Invalida tutte le sessioni refresh attive dell'utente
        refreshTokenRepository.revokeAllByUserId(user.getId());
        log.info("Password updated successfully and refresh tokens revoked for user: {}", user.getId());

        return new MessageResponse("La tua password è stata reimpostata con successo. Ora puoi effettuare il login con la nuova password.");
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 non disponibile", e);
        }
    }

    private AuthResponse issueAuthTokens(User user) {
        String accessToken = tokenProvider.generateAccessToken(user.getId());
        RefreshToken refreshToken = createRefreshToken(user);
        UserResponse userResponse = userMapper.toResponse(user);
        return AuthResponse.of(accessToken, refreshToken.getToken(), tokenProvider.getExpirationSeconds(), userResponse);
    }

    private RefreshToken createRefreshToken(User user) {
        String tokenString = tokenProvider.generateRefreshTokenString();
        Instant expiresAt = Instant.now().plusSeconds(tokenProvider.getRefreshExpirationSeconds());
        RefreshToken token = new RefreshToken(user, tokenString, expiresAt);
        return refreshTokenRepository.save(token);
    }
}
