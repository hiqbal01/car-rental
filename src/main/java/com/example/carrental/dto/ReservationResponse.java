package com.example.carrental.dto;

import com.example.carrental.domain.CarType;
import com.example.carrental.domain.Reservation;
import java.time.Instant;
import java.util.UUID;

public final class ReservationResponse {
    private final UUID id;
    private final UUID holdId;
    private final String carId;
    private final CarType carType;
    private final Instant start;
    private final Instant end;

    public ReservationResponse(Reservation source) {
        this.id = source.id();
        this.holdId = source.holdId();
        this.carId = source.car().id();
        this.carType = source.car().type();
        this.start = source.interval().start();
        this.end = source.interval().end();
    }

    public UUID getId() { return id; }

    public UUID getHoldId() { return holdId; }

    public String getCarId() { return carId; }

    public CarType getCarType() { return carType; }

    public Instant getStart() { return start; }

    public Instant getEnd() { return end; }
}
