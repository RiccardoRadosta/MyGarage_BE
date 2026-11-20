package com.ilmiogarage.repository;

import com.ilmiogarage.model.entity.UserSubscription;
import com.ilmiogarage.model.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {

    List<UserSubscription> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("""
        SELECT s FROM UserSubscription s
        WHERE s.user.id = :userId
          AND s.status IN (:activeStatuses)
          AND s.expiresAt IS NOT NULL
          AND s.expiresAt > :now
        ORDER BY s.expiresAt DESC, s.createdAt DESC, s.id DESC
    """)
    List<UserSubscription> findActiveSubscriptions(
            @Param("userId") UUID userId,
            @Param("activeStatuses") List<SubscriptionStatus> activeStatuses,
            @Param("now") Instant now);

    Optional<UserSubscription> findByPurchaseToken(String purchaseToken);

    @Query("SELECT MAX(s.startsAt) FROM UserSubscription s WHERE s.user.id = :userId AND s.startsAt IS NOT NULL")
    Optional<Instant> findMaxStartedAtByUserId(@Param("userId") UUID userId);
}
