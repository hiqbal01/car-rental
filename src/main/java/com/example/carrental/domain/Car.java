package com.example.carrental.domain;

import java.util.Objects;

public final class Car {
    private final String id;
    private final CarType type;

    public Car(String id, CarType type) {
        Objects.requireNonNull(id, "Car ID is required");
        Objects.requireNonNull(type, "Car type is required");
        if (id.isBlank()) throw new IllegalArgumentException("Car ID must not be blank");
        this.id = id;
        this.type = type;
    }

    public String id() {
        return id;
    }

    public CarType type() {
        return type;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Car other)) return false;
        return Objects.equals(id, other.id)
                && Objects.equals(type, other.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }

    @Override
    public String toString() {
        return "Car[" + "id=" + id + ", " + "type=" + type + "]";
    }
}
