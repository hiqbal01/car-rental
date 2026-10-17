package com.example.carrental.service;

import com.example.carrental.domain.BookingInterval;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/** Converts rental-location dates into unambiguous instants. */
public class BookingTimeResolver {
    private final ZoneId zone;

    public BookingTimeResolver(ZoneId zone) {
        this.zone = Objects.requireNonNull(zone);
    }

    public BookingInterval resolve(LocalDate startDate, LocalTime startTime,
                                   LocalDate endDate, LocalTime endTime) {
        return new BookingInterval(toInstant(startDate, startTime), toInstant(endDate, endTime));
    }

    public BookingInterval resolve(LocalDate startDate, LocalTime startTime,
                                   LocalDate endDate, LocalTime endTime, Integer days) {
        if (startDate == null) throw new IllegalArgumentException("Start date is required");
        if ((endDate == null) == (days == null)) {
            throw new IllegalArgumentException("Provide either endDate or days, exclusively");
        }
        if (days != null) {
            if (days <= 0) throw new IllegalArgumentException("Days must be positive");
            if (endTime != null) throw new IllegalArgumentException("endTime cannot be combined with days");
            // Calendar days preserve local pickup time across daylight-saving changes.
            return resolve(startDate, startTime, startDate.plusDays(days), startTime);
        }
        return resolve(startDate, startTime, endDate, endTime);
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        Objects.requireNonNull(date, "Booking date is required");
        LocalDateTime local = date.atTime(time == null ? LocalTime.of(10, 0) : time);
        var offsets = zone.getRules().getValidOffsets(local);
        if (offsets.size() != 1) {
            throw new IllegalArgumentException("Time " + local + " is ambiguous or nonexistent in " + zone);
        }
        return local.toInstant(offsets.get(0));
    }
}
