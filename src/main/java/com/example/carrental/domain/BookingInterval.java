package com.example.carrental.domain;

import java.time.Instant;
import java.util.Objects;

/** A half-open interval: start is included, end is excluded. */
public final class BookingInterval {
    private final Instant start;
    private final Instant end;

    public BookingInterval(Instant start, Instant end) {
        Objects.requireNonNull(start, "Start is required");
        Objects.requireNonNull(end, "End is required");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End must be after start");
        }
        this.start = start;
        this.end = end;
    }

    public Instant start() {
        return start;
    }

    public Instant end() {
        return end;
    }

    public boolean overlaps(BookingInterval other) {
        Objects.requireNonNull(other, "Other interval is required");
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof BookingInterval other)) return false;
        return Objects.equals(start, other.start)
                && Objects.equals(end, other.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return "BookingInterval[" + "start=" + start + ", " + "end=" + end + "]";
    }
}
