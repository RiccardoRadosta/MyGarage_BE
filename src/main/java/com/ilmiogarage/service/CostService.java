package com.ilmiogarage.service;

import com.ilmiogarage.dto.cost.CostByTypeResponse;
import com.ilmiogarage.dto.cost.CostSummaryResponse;
import com.ilmiogarage.dto.maintenance.MaintenanceResponse;
import com.ilmiogarage.mapper.MaintenanceMapper;
import com.ilmiogarage.model.entity.MaintenanceRecord;
import com.ilmiogarage.model.enums.MaintenanceType;
import com.ilmiogarage.repository.MaintenanceRecordRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CostService {

    private final MaintenanceRecordRepository maintenanceRepository;
    private final MaintenanceMapper maintenanceMapper;

    public CostService(
            MaintenanceRecordRepository maintenanceRepository,
            MaintenanceMapper maintenanceMapper) {
        this.maintenanceRepository = maintenanceRepository;
        this.maintenanceMapper = maintenanceMapper;
    }

    @Transactional(readOnly = true)
    public CostSummaryResponse getCostSummary(UUID ownerId, Integer requestedYear) {
        int year = (requestedYear != null)
                ? requestedYear
                : LocalDate.now(ZoneOffset.UTC).getYear();

        LocalDate startOfYear = LocalDate.of(year, 1, 1);
        LocalDate endOfYear = LocalDate.of(year, 12, 31);

        // All-time total spent for user
        BigDecimal totalSpent = maintenanceRepository.sumCostByOwnerId(ownerId);
        if (totalSpent == null) {
            totalSpent = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            totalSpent = totalSpent.setScale(2, RoundingMode.HALF_UP);
        }

        // Year records for user
        List<MaintenanceRecord> yearRecords = maintenanceRepository.findAllByOwnerIdAndDateBetween(ownerId, startOfYear, endOfYear);

        BigDecimal yearSpent = yearRecords.stream()
                .map(MaintenanceRecord::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // Group year records by maintenance type
        Map<MaintenanceType, List<MaintenanceRecord>> byTypeMap = yearRecords.stream()
                .collect(Collectors.groupingBy(MaintenanceRecord::getType, LinkedHashMap::new, Collectors.toList()));

        List<CostByTypeResponse> byTypeList = new ArrayList<>();
        for (Map.Entry<MaintenanceType, List<MaintenanceRecord>> entry : byTypeMap.entrySet()) {
            MaintenanceType type = entry.getKey();
            List<MaintenanceRecord> records = entry.getValue();

            BigDecimal typeTotal = records.stream()
                    .map(MaintenanceRecord::getCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            byTypeList.add(new CostByTypeResponse(
                    type,
                    type.getDefaultLabel(),
                    records.size(),
                    typeTotal
            ));
        }

        // Sort by total descending
        byTypeList.sort(Comparator.comparing(CostByTypeResponse::total).reversed());

        // Up to six recent maintenances for cost screen
        List<MaintenanceRecord> recentRecords = maintenanceRepository.findRecentByOwnerId(
                ownerId, PageRequest.of(0, 6));
        List<MaintenanceResponse> recentResponses = recentRecords.stream()
                .map(maintenanceMapper::toResponse)
                .toList();

        return new CostSummaryResponse(
                "EUR",
                totalSpent,
                year,
                yearSpent,
                byTypeList,
                recentResponses
        );
    }
}
