package com.ilmiogarage.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum MaintenanceType {
    OIL_CHANGE("Cambio olio"),
    SERVICE("Tagliando"),
    BRAKES("Freni / pastiglie"),
    TIRE_CHANGE("Cambio gomme"),
    WIPER_CHANGE("Cambio tergicristalli"),
    INSPECTION("Revisione"),
    BATTERY("Batteria"),
    TIMING_BELT("Distribuzione"),
    OTHER("Altro");

    private final String defaultLabel;

    MaintenanceType(String defaultLabel) {
        this.defaultLabel = defaultLabel;
    }

    public String getDefaultLabel() {
        return defaultLabel;
    }

    public String resolveLabel(String customDescription) {
        if (this == OTHER) {
            return (customDescription != null && !customDescription.isBlank())
                    ? customDescription.trim()
                    : defaultLabel;
        }
        return defaultLabel;
    }

    @JsonCreator
    public static MaintenanceType fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return MaintenanceType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo di manutenzione non valido: '" + value + "'");
        }
    }
}
