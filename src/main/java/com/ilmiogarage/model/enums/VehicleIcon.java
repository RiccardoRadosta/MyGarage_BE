package com.ilmiogarage.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum VehicleIcon {
    carFront("carFront"),
    car("car"),
    motorbike("motorbike"),
    scooter("scooter"),
    truck("truck"),
    van("van");

    private final String value;

    VehicleIcon(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static VehicleIcon fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (VehicleIcon icon : values()) {
            if (icon.value.equalsIgnoreCase(value.trim())) {
                return icon;
            }
        }
        throw new IllegalArgumentException("Icona veicolo non valida: '" + value 
                + "'. Valori ammessi: carFront, car, motorbike, scooter, truck, van");
    }
}
