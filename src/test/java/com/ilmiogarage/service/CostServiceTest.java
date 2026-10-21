package com.ilmiogarage.service;

import com.ilmiogarage.dto.cost.CostByTypeResponse;
import com.ilmiogarage.dto.cost.CostSummaryResponse;
import com.ilmiogarage.mapper.MaintenanceMapper;
import com.ilmiogarage.model.entity.MaintenanceRecord;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.model.entity.Vehicle;
import com.ilmiogarage.model.enums.MaintenanceType;
import com.ilmiogarage.model.enums.VehicleIcon;
import com.ilmiogarage.repository.MaintenanceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostServiceTest {

    @Mock
    private MaintenanceRecordRepository maintenanceRepository;

    @Mock
    private MaintenanceMapper maintenanceMapper;

    @InjectMocks
    private CostService costService;

    private UUID ownerId;
    private User owner;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        owner = new User(ownerId, "Paolo Neri", "paolo@example.com", "hash");
        vehicle = new Vehicle(owner, VehicleIcon.car, "Audi", "A3", 2022, "EF789GH", 30000);
    }

    @Test
    @DisplayName("Calcolo costi aggregati per anno e per tipologia di intervento")
    void getCostSummary_aggregatesAccurately() {
        int year = 2026;
        LocalDate d1 = LocalDate.of(year, 3, 10);
        LocalDate d2 = LocalDate.of(year, 6, 15);
        LocalDate d3 = LocalDate.of(year, 8, 20);

        MaintenanceRecord r1 = new MaintenanceRecord(owner, vehicle, MaintenanceType.OIL_CHANGE, null, d1, 20000, new BigDecimal("100.00"), null);
        MaintenanceRecord r2 = new MaintenanceRecord(owner, vehicle, MaintenanceType.OIL_CHANGE, null, d2, 25000, new BigDecimal("120.00"), null);
        MaintenanceRecord r3 = new MaintenanceRecord(owner, vehicle, MaintenanceType.BRAKES, null, d3, 28000, new BigDecimal("250.50"), null);

        List<MaintenanceRecord> yearRecords = List.of(r1, r2, r3);

        when(maintenanceRepository.sumCostByOwnerId(ownerId)).thenReturn(new BigDecimal("1470.50"));
        when(maintenanceRepository.findAllByOwnerIdAndDateBetween(eq(ownerId), any(), any())).thenReturn(yearRecords);
        when(maintenanceRepository.findRecentByOwnerId(eq(ownerId), any())).thenReturn(Collections.emptyList());

        CostSummaryResponse summary = costService.getCostSummary(ownerId, year);

        assertThat(summary.currency()).isEqualTo("EUR");
        assertThat(summary.year()).isEqualTo(year);
        assertThat(summary.totalSpent()).isEqualByComparingTo(new BigDecimal("1470.50"));
        assertThat(summary.yearSpent()).isEqualByComparingTo(new BigDecimal("470.50")); // 100 + 120 + 250.50

        assertThat(summary.byType()).hasSize(2);

        CostByTypeResponse brakesType = summary.byType().stream()
                .filter(b -> b.type() == MaintenanceType.BRAKES).findFirst().orElseThrow();
        assertThat(brakesType.count()).isEqualTo(1);
        assertThat(brakesType.total()).isEqualByComparingTo(new BigDecimal("250.50"));
        assertThat(brakesType.typeLabel()).isEqualTo("Freni / pastiglie");

        CostByTypeResponse oilType = summary.byType().stream()
                .filter(b -> b.type() == MaintenanceType.OIL_CHANGE).findFirst().orElseThrow();
        assertThat(oilType.count()).isEqualTo(2);
        assertThat(oilType.total()).isEqualByComparingTo(new BigDecimal("220.00"));
        assertThat(oilType.typeLabel()).isEqualTo("Cambio olio");
    }

    @Test
    @DisplayName("Nessun record restituisce totali a zero")
    void getCostSummary_emptyRecords_returnsZero() {
        when(maintenanceRepository.sumCostByOwnerId(ownerId)).thenReturn(BigDecimal.ZERO);
        when(maintenanceRepository.findAllByOwnerIdAndDateBetween(eq(ownerId), any(), any())).thenReturn(Collections.emptyList());
        when(maintenanceRepository.findRecentByOwnerId(eq(ownerId), any())).thenReturn(Collections.emptyList());

        CostSummaryResponse summary = costService.getCostSummary(ownerId, 2026);

        assertThat(summary.totalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.yearSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.byType()).isEmpty();
    }
}
