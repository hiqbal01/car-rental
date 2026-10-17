package com.example.carrental.domain;

import java.util.Objects;
import java.util.UUID;

public final class Reservation {
    private final UUID id;
    private final UUID holdId;
    private final Car car;
    private final BookingInterval interval;

    public Reservation(UUID id, UUID holdId, Car car, BookingInterval interval) {
        this.id = id;
        this.holdId = holdId;
        this.car = car;
        this.interval = interval;
    }

    public UUID id() {
        return id;
    }

    public UUID holdId() {
        return holdId;
    }

    public Car car() {
        return car;
    }

    public BookingInterval interval() {
        return interval;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Reservation other)) return false;
        return Objects.equals(id, other.id)
                && Objects.equals(holdId, other.holdId)
                && Objects.equals(car, other.car)
                && Objects.equals(interval, other.interval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, holdId, car, interval);
    }

    @Override
    public String toString() {
        return "Reservation[" + "id=" + id + ", " + "holdId=" + holdId + ", " + "car=" + car + ", " + "interval=" + interval + "]";
    }
}
