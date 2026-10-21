package com.ilmiogarage.repository;

import com.ilmiogarage.model.entity.MaintenanceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, UUID>, JpaSpecificationExecutor<MaintenanceRecord> {

    Page<MaintenanceRecord> findByVehicleIdAndOwnerId(UUID vehicleId, UUID ownerId, Pageable pageable);

    Optional<MaintenanceRecord> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<MaintenanceRecord> findFirstByVehicleIdAndOwnerIdOrderByDateDescCreatedAtDesc(UUID vehicleId, UUID ownerId);

    long countByOwnerId(UUID ownerId);

    long countByVehicleIdAndOwnerId(UUID vehicleId, UUID ownerId);

    @Query("SELECT m FROM MaintenanceRecord m WHERE m.owner.id = :ownerId ORDER BY m.date DESC, m.createdAt DESC")
    List<MaintenanceRecord> findRecentByOwnerId(@Param("ownerId") UUID ownerId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(m.cost), 0) FROM MaintenanceRecord m WHERE m.owner.id = :ownerId")
    BigDecimal sumCostByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("SELECT COALESCE(SUM(m.cost), 0) FROM MaintenanceRecord m WHERE m.owner.id = :ownerId AND m.date >= :startDate AND m.date <= :endDate")
    BigDecimal sumCostByOwnerIdAndDateBetween(@Param("ownerId") UUID ownerId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT m FROM MaintenanceRecord m WHERE m.owner.id = :ownerId")
    List<MaintenanceRecord> findAllByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("SELECT m FROM MaintenanceRecord m WHERE m.owner.id = :ownerId AND m.date >= :startDate AND m.date <= :endDate")
    List<MaintenanceRecord> findAllByOwnerIdAndDateBetween(@Param("ownerId") UUID ownerId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
