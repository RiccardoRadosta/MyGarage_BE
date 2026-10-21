package com.ilmiogarage.service;

import com.ilmiogarage.dto.user.UpdateUserRequest;
import com.ilmiogarage.dto.user.UserResponse;
import com.ilmiogarage.exception.EmailAlreadyExistsException;
import com.ilmiogarage.exception.ResourceNotFoundException;
import com.ilmiogarage.mapper.UserMapper;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato."));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato."));

        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }

        if (request.email() != null && !request.email().isBlank()) {
            String newEmail = request.email().trim().toLowerCase();
            if (!newEmail.equalsIgnoreCase(user.getEmail())) {
                Optional<User> existingUser = userRepository.findByEmail(newEmail);
                if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
                    log.warn("Update profile failed: email {} already in use", newEmail);
                    throw new EmailAlreadyExistsException("L'indirizzo email '" + newEmail + "' è già registrato.");
                }
                user.setEmail(newEmail);
            }
        }

        User updatedUser = userRepository.save(user);
        log.info("Profile updated for user: {}", userId);
        return userMapper.toResponse(updatedUser);
    }
}
