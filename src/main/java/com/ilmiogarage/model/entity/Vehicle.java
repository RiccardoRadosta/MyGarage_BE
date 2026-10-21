package com.ilmiogarage.model.entity;

import com.ilmiogarage.model.enums.VehicleIcon;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VehicleIcon icon;

    @Column(nullable = false, length = 80)
    private String brand;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "\"year\"", nullable = false)
    private Integer year;

    @Column(nullable = false, length = 30)
    private String plate;

    @Column(nullable = false)
    private Integer mileage;

    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MaintenanceRecord> maintenances = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Vehicle() {
    }

    public Vehicle(User owner, VehicleIcon icon, String brand, String model, Integer year, String plate, Integer mileage) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.icon = icon;
        this.brand = brand;
        this.model = model;
        this.year = year;
        this.plate = plate != null ? plate.trim().toUpperCase() : null;
        this.mileage = mileage;
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
        if (this.plate != null) {
            this.plate = this.plate.trim().toUpperCase();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
        if (this.plate != null) {
            this.plate = this.plate.trim().toUpperCase();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public VehicleIcon getIcon() {
        return icon;
    }

    public void setIcon(VehicleIcon icon) {
        this.icon = icon;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getPlate() {
        return plate;
    }

    public void setPlate(String plate) {
        this.plate = plate != null ? plate.trim().toUpperCase() : null;
    }

    public Integer getMileage() {
        return mileage;
    }

    public void setMileage(Integer mileage) {
        this.mileage = mileage;
    }

    public List<MaintenanceRecord> getMaintenances() {
        return maintenances;
    }

    public void setMaintenances(List<MaintenanceRecord> maintenances) {
        this.maintenances = maintenances;
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
