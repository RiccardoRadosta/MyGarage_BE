package com.ilmiogarage.repository;

import com.ilmiogarage.model.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    Optional<Vehicle> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByOwnerIdAndPlateIgnoreCase(UUID ownerId, String plate);

    boolean existsByOwnerIdAndPlateIgnoreCaseAndIdNot(UUID ownerId, String plate, UUID id);

    int countByOwnerId(UUID ownerId);
}
