package com.ilmiogarage.model.entity;

import com.ilmiogarage.model.enums.UserRole;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "google_sub", length = 255, unique = true)
    private String googleSub;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Column(name = "primary_vehicle_selected_at")
    private Instant primaryVehicleSelectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User() {
    }

    public User(UUID id, String name, String email, String passwordHash) {
        this(id, name, email, passwordHash, null);
    }

    public User(UUID id, String name, String email, String passwordHash, String googleSub) {
        this(id, name, email, passwordHash, googleSub, UserRole.USER);
    }

    public User(UUID id, String name, String email, String passwordHash, String googleSub, UserRole role) {
        this.id = id != null ? id : UUID.randomUUID();
        this.name = name;
        this.email = email != null ? email.trim().toLowerCase() : null;
        this.passwordHash = passwordHash;
        this.googleSub = googleSub;
        this.role = role != null ? role : UserRole.USER;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.role == null) {
            this.role = UserRole.USER;
        }
        this.updatedAt = now;
        if (this.email != null) {
            this.email = this.email.trim().toLowerCase();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
        if (this.email != null) {
            this.email = this.email.trim().toLowerCase();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email != null ? email.trim().toLowerCase() : null;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public void setGoogleSub(String googleSub) {
        this.googleSub = googleSub;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role != null ? role : UserRole.USER;
    }

    public Instant getPrimaryVehicleSelectedAt() {
        return primaryVehicleSelectedAt;
    }

    public void setPrimaryVehicleSelectedAt(Instant primaryVehicleSelectedAt) {
        this.primaryVehicleSelectedAt = primaryVehicleSelectedAt != null
                ? primaryVehicleSelectedAt.truncatedTo(ChronoUnit.MICROS)
                : null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
