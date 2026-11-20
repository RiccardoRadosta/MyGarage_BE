package com.ilmiogarage.exception;

import org.springframework.http.HttpStatus;

public class PrimaryVehicleSelectionRequiredException extends ApiException {

    public PrimaryVehicleSelectionRequiredException() {
        this("È necessario selezionare il veicolo principale prima di poter eseguire modifiche.");
    }

    public PrimaryVehicleSelectionRequiredException(String message) {
        super(HttpStatus.FORBIDDEN, ErrorCode.PRIMARY_VEHICLE_SELECTION_REQUIRED, message);
    }
}
