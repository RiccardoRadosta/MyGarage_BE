package com.ilmiogarage.service;

import com.ilmiogarage.dto.auth.AuthResponse;
import com.ilmiogarage.dto.auth.LoginRequest;
import com.ilmiogarage.dto.auth.LogoutRequest;
import com.ilmiogarage.dto.auth.RefreshRequest;
import com.ilmiogarage.dto.auth.RegisterRequest;
import com.ilmiogarage.dto.auth.TokenRefreshResponse;
import com.ilmiogarage.dto.user.UserResponse;
import com.ilmiogarage.exception.EmailAlreadyExistsException;
import com.ilmiogarage.exception.UnauthorizedException;
import com.ilmiogarage.mapper.UserMapper;
import com.ilmiogarage.model.entity.RefreshToken;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.repository.RefreshTokenRepository;
import com.ilmiogarage.repository.UserRepository;
import com.ilmiogarage.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final UserMapper userMapper;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            UserMapper userMapper) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.userMapper = userMapper;
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

        String accessToken = tokenProvider.generateAccessToken(savedUser.getId());
        RefreshToken refreshToken = createRefreshToken(savedUser);

        UserResponse userResponse = userMapper.toResponse(savedUser);
        return AuthResponse.of(accessToken, refreshToken.getToken(), tokenProvider.getExpirationSeconds(), userResponse);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("Login failed: user not found [{}]", normalizedEmail);
                    return new UnauthorizedException("Credenziali non valide.");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed: invalid password for user [{}]", normalizedEmail);
            throw new UnauthorizedException("Credenziali non valide.");
        }

        String accessToken = tokenProvider.generateAccessToken(user.getId());
        RefreshToken refreshToken = createRefreshToken(user);

        log.info("User {} logged in successfully", user.getId());
        UserResponse userResponse = userMapper.toResponse(user);
        return AuthResponse.of(accessToken, refreshToken.getToken(), tokenProvider.getExpirationSeconds(), userResponse);
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

    private RefreshToken createRefreshToken(User user) {
        String tokenString = tokenProvider.generateRefreshTokenString();
        Instant expiresAt = Instant.now().plusSeconds(tokenProvider.getRefreshExpirationSeconds());
        RefreshToken token = new RefreshToken(user, tokenString, expiresAt);
        return refreshTokenRepository.save(token);
    }
}
