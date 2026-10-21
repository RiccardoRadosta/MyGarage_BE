package com.ilmiogarage.dto.cost;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ilmiogarage.model.enums.MaintenanceType;
import java.math.BigDecimal;

public record CostByTypeResponse(
        MaintenanceType type,
        String typeLabel,
        long count,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
        BigDecimal total
) {
}
