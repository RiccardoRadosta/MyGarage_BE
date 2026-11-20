package com.ilmiogarage.dto.subscription;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ilmiogarage.model.enums.GrantType;
import com.ilmiogarage.model.enums.SubscriptionStatus;
import com.ilmiogarage.model.enums.SubscriptionTier;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO descrittivo dello stato dell'abbonamento e del piano dell'utente.
 */
public record UserPlanInfo(
        SubscriptionTier tier,
        boolean isPremium,
        PlanOrigin origin,
        GrantType grantType,
        SubscriptionStatus subscriptionStatus,
        String productId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant expiresAt,
        boolean autoRenewing,
        boolean isPermanent,
        UUID primaryVehicleId,
        boolean requiresPrimarySelection
) {
    public enum PlanOrigin {
        GRANT,
        GOOGLE_PLAY,
        FREE_TIER
    }

    public static UserPlanInfo free(UUID primaryVehicleId, boolean requiresPrimarySelection) {
        return new UserPlanInfo(
                SubscriptionTier.FREE,
                false,
                PlanOrigin.FREE_TIER,
                null,
                null,
                null,
                null,
                false,
                false,
                primaryVehicleId,
                requiresPrimarySelection
        );
    }

    public static UserPlanInfo free(UUID primaryVehicleId) {
        return free(primaryVehicleId, false);
    }

    public static UserPlanInfo free() {
        return free(null, false);
    }

    public static UserPlanInfo fromGrant(GrantType grantType, Instant expiresAt, UUID primaryVehicleId) {
        boolean permanent = (expiresAt == null);
        return new UserPlanInfo(
                SubscriptionTier.PREMIUM,
                true,
                PlanOrigin.GRANT,
                grantType,
                null,
                null,
                expiresAt,
                false,
                permanent,
                primaryVehicleId,
                false
        );
    }

    public static UserPlanInfo fromGrant(GrantType grantType, Instant expiresAt) {
        return fromGrant(grantType, expiresAt, null);
    }

    public static UserPlanInfo fromSubscription(
            String productId,
            SubscriptionStatus status,
            Instant expiresAt,
            boolean autoRenewing,
            UUID primaryVehicleId) {
        return new UserPlanInfo(
                SubscriptionTier.PREMIUM,
                true,
                PlanOrigin.GOOGLE_PLAY,
                null,
                status,
                productId,
                expiresAt,
                autoRenewing,
                false,
                primaryVehicleId,
                false
        );
    }

    public static UserPlanInfo fromSubscription(String productId, Instant expiresAt, boolean autoRenewing) {
        return fromSubscription(productId, SubscriptionStatus.ACTIVE, expiresAt, autoRenewing, null);
    }
}
