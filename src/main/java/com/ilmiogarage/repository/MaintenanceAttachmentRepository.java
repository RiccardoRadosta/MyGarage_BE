package com.ilmiogarage.repository;

import com.ilmiogarage.model.entity.MaintenanceAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaintenanceAttachmentRepository extends JpaRepository<MaintenanceAttachment, UUID> {

    Optional<MaintenanceAttachment> findByMaintenanceId(UUID maintenanceId);

    boolean existsByMaintenanceId(UUID maintenanceId);

    /**
     * Restituisce solo i metadati dell'allegato (senza il campo data/BYTEA),
     * per evitare di caricare dati binari nelle risposte di lista/dettaglio.
     */
    @Query("SELECT a FROM MaintenanceAttachment a WHERE a.maintenance.id = :maintenanceId")
    Optional<MaintenanceAttachment> findMetadataByMaintenanceId(@Param("maintenanceId") UUID maintenanceId);

    void deleteByMaintenanceId(UUID maintenanceId);
}
