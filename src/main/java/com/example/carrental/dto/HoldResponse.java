package com.example.carrental.dto;

import com.example.carrental.domain.CarType;
import com.example.carrental.domain.Hold;
import java.time.Instant;
import java.util.UUID;

public final class HoldResponse {
    private final UUID id;
    private final String carId;
    private final CarType carType;
    private final Instant start;
    private final Instant end;
    private final Instant expiresAt;

    public HoldResponse(Hold source) {
        this.id = source.id();
        this.carId = source.car().id();
        this.carType = source.car().type();
        this.start = source.interval().start();
        this.end = source.interval().end();
        this.expiresAt = source.expiresAt();
    }

    public UUID getId() { return id; }

    public String getCarId() { return carId; }

    public CarType getCarType() { return carType; }

    public Instant getStart() { return start; }

    public Instant getEnd() { return end; }

    public Instant getExpiresAt() { return expiresAt; }
}
