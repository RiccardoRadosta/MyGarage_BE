package com.ilmiogarage.dto.cost;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ilmiogarage.dto.maintenance.MaintenanceResponse;
import java.math.BigDecimal;
import java.util.List;

public record CostSummaryResponse(
        String currency,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
        BigDecimal totalSpent,
        int year,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
        BigDecimal yearSpent,
        List<CostByTypeResponse> byType,
        List<MaintenanceResponse> recentMaintenances
) {
}
