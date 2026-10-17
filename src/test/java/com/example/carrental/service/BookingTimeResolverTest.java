package com.example.carrental.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingTimeResolverTest {
    private final BookingTimeResolver resolver = new BookingTimeResolver(ZoneId.of("America/New_York"));

    @Test
    void calendarDaysPreserveLocalTimeAcrossDaylightSaving() {
        var interval = resolver.resolve(LocalDate.of(2026, 3, 7), null, null, null, 1);
        assertEquals(Instant.parse("2026-03-07T15:00:00Z"), interval.start(),
                "Missing pickup time must default to 10 a.m. New York time");
        assertEquals(Instant.parse("2026-03-08T14:00:00Z"), interval.end(),
                "One calendar day must preserve 10 a.m. local time across the spring transition");
        var explicit = resolver.resolve(LocalDate.of(2026, 10, 31), LocalTime.NOON, null, null, 1);
        assertEquals(Instant.parse("2026-11-01T17:00:00Z"), explicit.end(),
                "Calendar days must preserve an explicit pickup time across the fall transition");
    }

    @Test
    void rejectsAmbiguousDurationInputs() {
        LocalDate start = LocalDate.of(2030, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(start, null, null, null, null),
                "A booking must supply an end date or a day count");
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(start, null, start.plusDays(1), null, 1),
                "End date and day count must not be supplied together");
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(start, null, null, null, 0),
                "Day count must be positive");
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(start, null, null, null, -1),
                "Negative day counts must be rejected");
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(start, null, null, LocalTime.NOON, 1),
                "Day-count requests must derive the end time from the pickup time");
    }

    @Test
    void defaultsEachMissingTimeToTenInRentalZone() {
        var interval = resolver.resolve(LocalDate.of(2030, 1, 1), null,
                LocalDate.of(2030, 1, 2), null);
        assertEquals(Instant.parse("2030-01-01T15:00:00Z"), interval.start(),
                "An omitted start time must default to 10 a.m. in the rental timezone");
        assertEquals(Instant.parse("2030-01-02T15:00:00Z"), interval.end(),
                "An omitted end time must default to 10 a.m. in the rental timezone");
        var mixed = resolver.resolve(LocalDate.of(2030, 1, 1), LocalTime.NOON,
                LocalDate.of(2030, 1, 2), null);
        assertEquals(Instant.parse("2030-01-01T17:00:00Z"), mixed.start(),
                "An explicit start time must be preserved and converted from the rental timezone to UTC");
        assertEquals(interval.end(), mixed.end(),
                "The end time must default independently when an explicit start time is provided");
    }

    @Test
    void rejectsDaylightSavingGapAndAmbiguity() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(
                LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), LocalDate.of(2026, 3, 9), null),
                "Nonexistent local times during the daylight-saving spring gap must be rejected");
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(
                LocalDate.of(2026, 11, 1), LocalTime.of(1, 30), LocalDate.of(2026, 11, 2), null),
                "Ambiguous local times during the daylight-saving fall overlap must be rejected");
    }

    @Test
    void rejectsEndBeforeDefaultedStart() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(
                LocalDate.of(2030, 1, 1), null, LocalDate.of(2030, 1, 1), LocalTime.of(9, 0)),
                "End must follow start after applying the default time");
    }
}
