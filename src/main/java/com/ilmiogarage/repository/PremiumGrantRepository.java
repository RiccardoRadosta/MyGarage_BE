package com.ilmiogarage.repository;

import com.ilmiogarage.model.entity.PremiumGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PremiumGrantRepository extends JpaRepository<PremiumGrant, UUID> {

    List<PremiumGrant> findAllByUserIdOrderByGrantedAtDesc(UUID userId);

    @Query("""
        SELECT g FROM PremiumGrant g
        WHERE g.user.id = :userId
          AND g.revokedAt IS NULL
          AND (g.expiresAt IS NULL OR g.expiresAt > :now)
        ORDER BY g.grantedAt DESC
    """)
    List<PremiumGrant> findValidGrants(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("SELECT MAX(g.grantedAt) FROM PremiumGrant g WHERE g.user.id = :userId")
    Optional<Instant> findMaxGrantedAtByUserId(@Param("userId") UUID userId);
}
