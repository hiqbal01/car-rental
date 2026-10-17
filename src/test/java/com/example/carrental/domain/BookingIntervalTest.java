package com.example.carrental.domain;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingIntervalTest {
    private final BookingInterval booking = interval(10, 14);

    @ParameterizedTest
    @CsvSource({"8,10,false", "14,16,false", "8,9,false", "15,16,false",
            "9,11,true", "13,15,true", "11,13,true", "9,15,true", "10,14,true"})
    void detectsOverlapSymmetrically(int start, int end, boolean expected) {
        BookingInterval other = interval(start, end);
        assertEquals(expected, booking.overlaps(other),
                "Intervals must overlap only when they share time; touching endpoints are allowed");
        assertEquals(expected, other.overlaps(booking),
                "Overlap detection must be symmetric regardless of interval order");
    }

    @Test
    void rejectsEmptyOrReversedIntervals() {
        assertThrows(IllegalArgumentException.class, () -> interval(10, 10),
                "Booking end must be strictly later than start; zero-length bookings are invalid");
        assertThrows(IllegalArgumentException.class, () -> interval(11, 10),
                "Booking end must be strictly later than start; reversed bookings are invalid");
    }

    @Test
    void requiresBothBoundaries() {
        Instant now = Instant.parse("2030-01-01T10:00:00Z");
        assertThrows(NullPointerException.class, () -> new BookingInterval(null, now),
                "A booking interval must have a start instant");
        assertThrows(NullPointerException.class, () -> new BookingInterval(now, null),
                "A booking interval must have an end instant");
    }

    private static BookingInterval interval(int startHour, int endHour) {
        Instant midnight = Instant.parse("2030-01-01T00:00:00Z");
        return new BookingInterval(midnight.plusSeconds(startHour * 3600L),
                midnight.plusSeconds(endHour * 3600L));
    }
}
