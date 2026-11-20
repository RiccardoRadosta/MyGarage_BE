package com.ilmiogarage.model.entity;

import com.ilmiogarage.model.enums.SubscriptionStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_subscriptions")
public class UserSubscription {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String provider = "GOOGLE_PLAY";

    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    @Column(name = "purchase_token", length = 512)
    private String purchaseToken;

    @Column(name = "order_id", length = 100)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "auto_renewing", nullable = false)
    private boolean autoRenewing = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserSubscription() {
    }

    public UserSubscription(User user, String provider, String productId, String purchaseToken,
                            String orderId, SubscriptionStatus status, Instant startsAt,
                            Instant expiresAt, boolean autoRenewing) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.provider = provider != null ? provider : "GOOGLE_PLAY";
        this.productId = productId;
        this.purchaseToken = purchaseToken;
        this.orderId = orderId;
        this.status = status != null ? status : SubscriptionStatus.ACTIVE;
        this.startsAt = startsAt;
        this.expiresAt = expiresAt;
        this.autoRenewing = autoRenewing;
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
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getPurchaseToken() {
        return purchaseToken;
    }

    public void setPurchaseToken(String purchaseToken) {
        this.purchaseToken = purchaseToken;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isAutoRenewing() {
        return autoRenewing;
    }

    public void setAutoRenewing(boolean autoRenewing) {
        this.autoRenewing = autoRenewing;
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
