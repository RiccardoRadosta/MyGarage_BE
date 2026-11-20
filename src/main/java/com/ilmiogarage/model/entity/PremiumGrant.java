package com.ilmiogarage.model.entity;

import com.ilmiogarage.model.enums.GrantType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "premium_grants")
public class PremiumGrant {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "grant_type", nullable = false, length = 30)
    private GrantType grantType;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "granted_by", nullable = false, length = 100)
    private String grantedBy;

    @Column(length = 255)
    private String notes;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PremiumGrant() {
    }

    public PremiumGrant(User user, GrantType grantType, Instant grantedAt, String grantedBy,
                        String notes, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.grantType = grantType;
        this.grantedAt = grantedAt != null ? grantedAt : Instant.now();
        this.grantedBy = grantedBy;
        this.notes = notes;
        this.expiresAt = expiresAt;
        this.revokedAt = null;
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
        if (this.grantedAt == null) {
            this.grantedAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isValidAt(Instant instant) {
        if (revokedAt != null) {
            return false;
        }
        return expiresAt == null || expiresAt.isAfter(instant);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public GrantType getGrantType() {
        return grantType;
    }

    public void setGrantType(GrantType grantType) {
        this.grantType = grantType;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(Instant grantedAt) {
        this.grantedAt = grantedAt;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
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
