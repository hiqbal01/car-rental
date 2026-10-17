package com.example.carrental.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Hold {
    private final UUID id;
    private final Car car;
    private final BookingInterval interval;
    private final Instant expiresAt;

    public Hold(UUID id, Car car, BookingInterval interval, Instant expiresAt) {
        this.id = id;
        this.car = car;
        this.interval = interval;
        this.expiresAt = expiresAt;
    }

    public UUID id() {
        return id;
    }

    public Car car() {
        return car;
    }

    public BookingInterval interval() {
        return interval;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean isActiveAt(Instant now) {
        return now.isBefore(expiresAt);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Hold other)) return false;
        return Objects.equals(id, other.id)
                && Objects.equals(car, other.car)
                && Objects.equals(interval, other.interval)
                && Objects.equals(expiresAt, other.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, car, interval, expiresAt);
    }

    @Override
    public String toString() {
        return "Hold[" + "id=" + id + ", " + "car=" + car + ", " + "interval=" + interval + ", " + "expiresAt=" + expiresAt + "]";
    }
}
