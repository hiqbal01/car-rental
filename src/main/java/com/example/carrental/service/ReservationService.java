package com.example.carrental.service;

import com.example.carrental.domain.BookingInterval;
import com.example.carrental.domain.Car;
import com.example.carrental.domain.CarType;
import com.example.carrental.domain.Hold;
import com.example.carrental.domain.Reservation;
import com.example.carrental.exception.BookingException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.example.carrental.exception.BookingException.Reason.HOLD_EXPIRED;
import static com.example.carrental.exception.BookingException.Reason.HOLD_NOT_FOUND;
import static com.example.carrental.exception.BookingException.Reason.START_IN_PAST;
import static com.example.carrental.exception.BookingException.Reason.UNAVAILABLE;

/** All mutable booking state is guarded by this instance's monitor. Single-process only. */
public class ReservationService {
    private final List<Car> fleet;
    private final Clock clock;
    private final Duration holdTtl;
    private final Map<UUID, Hold> holds = new HashMap<>();
    private final Map<UUID, Reservation> reservationsByHold = new HashMap<>();

    public ReservationService(List<Car> fleet, Clock clock, Duration holdTtl) {
        this.fleet = List.copyOf(fleet);
        this.clock = Objects.requireNonNull(clock);
        this.holdTtl = Objects.requireNonNull(holdTtl);
        if (holdTtl.isZero() || holdTtl.isNegative()) {
            throw new IllegalArgumentException("Hold TTL must be positive");
        }
        if (fleet.stream().map(Car::id).distinct().count() != fleet.size()) {
            throw new IllegalArgumentException("Car IDs must be unique");
        }
    }

    public synchronized Hold hold(CarType type, BookingInterval interval) {
        Objects.requireNonNull(type, "Car type is required");
        Objects.requireNonNull(interval, "Interval is required");
        // Use one instant for validation, expiration checks, and the new hold's TTL.
        var now = clock.instant();
        validateStart(interval, now);
        Car car = availableCars(interval, now).stream()
                // Only consider the requested type; there are no automatic upgrades.
                .filter(candidate -> candidate.type() == type)
                // Choose the first available car in fleet order, or report exhausted capacity.
                .findFirst().orElseThrow(() -> new BookingException(UNAVAILABLE,
                        "No " + type + " available for the requested interval"));
        // The synchronized method keeps selection and insertion atomic: another caller
        // cannot claim this car for an overlapping period between these two steps.
        Hold hold = new Hold(UUID.randomUUID(), car, interval, now.plus(holdTtl));
        holds.put(hold.id(), hold);
        return hold;
    }

    /** A snapshot only: availability is checked again when creating a hold. */
    public synchronized Map<CarType, Long> availability(BookingInterval interval) {
        Objects.requireNonNull(interval, "Interval is required");
        Instant now = clock.instant();
        validateStart(interval, now);
        Map<CarType, Long> counts = new EnumMap<>(CarType.class);
        for (CarType type : CarType.values()) counts.put(type, 0L);
        for (Car car : availableCars(interval, now)) {
            counts.merge(car.type(), 1L, Long::sum);
        }
        return Map.copyOf(counts);
    }

    // Called only while holding this service's monitor, so both maps stay consistent.
    private List<Car> availableCars(BookingInterval interval, Instant now) {
        return fleet.stream()
                // Exclude cars with an active hold overlapping the requested booking period.
                // Expired holds and holds for other periods do not block availability.
                .filter(candidate -> holds.values().stream().noneMatch(existing ->
                        existing.car().equals(candidate) && existing.isActiveAt(now)
                                && existing.interval().overlaps(interval)))
                // Confirmed reservations block overlapping periods and have no TTL.
                .filter(candidate -> reservationsByHold.values().stream().noneMatch(existing ->
                        existing.car().equals(candidate) && existing.interval().overlaps(interval)))
                .toList();
    }

    private void validateStart(BookingInterval interval, Instant now) {
        if (interval.start().isBefore(now)) {
            throw new BookingException(START_IN_PAST, "Booking start must not be in the past");
        }
    }

    public synchronized Reservation confirm(UUID holdId) {
        Objects.requireNonNull(holdId, "Hold ID is required");
        // Repeated confirmation returns the original reservation, even after the hold TTL.
        Reservation confirmed = reservationsByHold.get(holdId);
        if (confirmed != null) return confirmed;
        Hold hold = holds.get(holdId);
        if (hold == null) throw new BookingException(HOLD_NOT_FOUND, "Hold not found");
        var now = clock.instant();
        if (!hold.isActiveAt(now)) throw new BookingException(HOLD_EXPIRED, "Hold has expired");
        if (hold.interval().start().isBefore(now)) {
            throw new BookingException(START_IN_PAST, "Booking start must not be in the past");
        }
        Reservation reservation = new Reservation(UUID.randomUUID(), holdId, hold.car(), hold.interval());
        reservationsByHold.put(holdId, reservation);
        holds.remove(holdId);
        return reservation;
    }

    /** Releasing an absent or already released hold is harmless; reservations are unaffected. */
    public synchronized void release(UUID holdId) {
        holds.remove(Objects.requireNonNull(holdId, "Hold ID is required"));
    }
}
