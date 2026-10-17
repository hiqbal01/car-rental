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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.carrental.exception.BookingException.Reason.HOLD_EXPIRED;
import static com.example.carrental.exception.BookingException.Reason.HOLD_NOT_FOUND;
import static com.example.carrental.exception.BookingException.Reason.START_IN_PAST;
import static com.example.carrental.exception.BookingException.Reason.UNAVAILABLE;

class ReservationServiceTest {
    private final MutableClock clock = new MutableClock();
    private final ReservationService service = new ReservationService(List.of(
            new Car("sedan-1", CarType.SEDAN), new Car("suv-1", CarType.SUV),
            new Car("van-1", CarType.VAN)), clock, Duration.ofMinutes(5));
    private final BookingInterval interval = new BookingInterval(
            Instant.parse("2030-01-02T10:00:00Z"), Instant.parse("2030-01-03T10:00:00Z"));

    @Test
    void availabilityCountsHoldsReservationsAndExpirationWithoutClaimingCars() {
        assertEquals(1L, service.availability(interval).get(CarType.SEDAN),
                "Availability must initially include every free sedan");
        Hold sedan = service.hold(CarType.SEDAN, interval);
        Hold suv = service.hold(CarType.SUV, interval);
        service.confirm(suv.id());
        assertEquals(0L, service.availability(interval).get(CarType.SEDAN),
                "Active overlapping holds must reduce available counts to zero");
        assertEquals(0L, service.availability(interval).get(CarType.SUV),
                "Confirmed reservations must reduce available counts to zero");
        assertEquals(1L, service.availability(interval).get(CarType.VAN),
                "Availability checks must not claim cars or consume other types");
        var adjacent = new BookingInterval(interval.end(), interval.end().plusSeconds(3600));
        assertEquals(1L, service.availability(adjacent).get(CarType.SEDAN),
                "Adjacent booking intervals must leave the same sedan available");
        clock.advance(Duration.ofMinutes(5));
        assertEquals(1L, service.availability(interval).get(CarType.SEDAN),
                "Expired holds must restore availability at the exact expiration boundary");
        assertEquals(0L, service.availability(interval).get(CarType.SUV),
                "Hold expiration must not free confirmed reservations");
        assertReason(HOLD_EXPIRED, () -> service.confirm(sedan.id()),
                "Browsing availability must not make an expired hold confirmable");
    }

    @Test
    void holdsBlockOnlyTheirTypeAndOverlappingInterval() {
        service.hold(CarType.SEDAN, interval);
        assertReason(UNAVAILABLE, () -> service.hold(CarType.SEDAN, interval),
                "An active overlapping hold must block the last car of its type");
        assertEquals(CarType.SUV, service.hold(CarType.SUV, interval).car().type(),
                "Holding the only sedan must still allow an SUV to be held");
        assertEquals(CarType.VAN, service.hold(CarType.VAN, interval).car().type(),
                "Holding the only sedan and SUV must still allow a van to be held");
        assertDoesNotThrow(() -> service.hold(CarType.SEDAN,
                new BookingInterval(interval.end(), interval.end().plusSeconds(3600))),
                "A sedan hold starting exactly when the existing hold's booking interval ends must succeed");
    }

    @Test
    void usesAllCarsBeforeReportingUnavailable() {
        var twoCars = new ReservationService(List.of(new Car("a", CarType.SEDAN),
                new Car("b", CarType.SEDAN)), clock, Duration.ofMinutes(5));
        assertNotEquals(twoCars.hold(CarType.SEDAN, interval).car(),
                twoCars.hold(CarType.SEDAN, interval).car(),
                "Overlapping holds must be assigned different cars while inventory remains");
        assertReason(UNAVAILABLE, () -> twoCars.hold(CarType.SEDAN, interval),
                "No further holds may be created when all cars of the requested type are occupied");
    }

    @Test
    void expirationAtExactBoundaryFreesCarAndPreventsOldConfirmation() {
        Hold old = service.hold(CarType.SEDAN, interval);
        clock.advance(Duration.ofMinutes(5));
        service.hold(CarType.SEDAN, interval);
        assertReason(HOLD_EXPIRED, () -> service.confirm(old.id()),
                "Confirmation must fail at the exact hold expiration instant");
    }

    @Test
    void confirmationIsIdempotentAndReservationSurvivesTtlAndRelease() {
        Hold hold = service.hold(CarType.SEDAN, interval);
        clock.advance(Duration.ofMinutes(5).minusSeconds(1));
        Reservation reservation = service.confirm(hold.id());
        clock.advance(Duration.ofMinutes(10));
        service.release(hold.id());
        assertEquals(reservation, service.confirm(hold.id()),
                "Repeated confirmation must return the original reservation even after TTL and release");
        assertReason(UNAVAILABLE, () -> service.hold(CarType.SEDAN, interval),
                "Confirmed reservations must continue blocking inventory after hold TTL and release");
    }

    @Test
    void releaseFreesCarAndIsSafeToRepeat() {
        Hold hold = service.hold(CarType.SEDAN, interval);
        service.release(hold.id());
        service.release(hold.id());
        assertReason(HOLD_NOT_FOUND, () -> service.confirm(hold.id()),
                "A released hold must no longer be confirmable");
        service.hold(CarType.SEDAN, interval);
    }

    @Test
    void rejectsPastStartsIncludingWhenConfirming() {
        var near = new BookingInterval(clock.instant().plusSeconds(1), clock.instant().plusSeconds(60));
        Hold hold = service.hold(CarType.SEDAN, near);
        clock.advance(Duration.ofSeconds(2));
        assertReason(START_IN_PAST, () -> service.confirm(hold.id()),
                "Confirmation must reject a booking whose start has passed");
        assertReason(START_IN_PAST, () -> service.hold(CarType.SEDAN, near),
                "Hold creation must reject a booking whose start is in the past");
    }

    @Test
    void rejectsUnknownHoldsAndInvalidConfiguration() {
        assertReason(HOLD_NOT_FOUND, () -> service.confirm(UUID.randomUUID()),
                "Confirmation must reject an unknown hold ID");
        assertThrows(IllegalArgumentException.class, () -> new ReservationService(
                List.of(), clock, Duration.ZERO),
                "Hold TTL must be strictly positive");
        Car car = new Car("duplicate", CarType.SEDAN);
        assertThrows(IllegalArgumentException.class, () -> new ReservationService(
                List.of(car, car), clock, Duration.ofMinutes(5)),
                "Every car in the fleet must have a unique ID");
    }

    @Test
    void concurrentRequestsForLastCarProduceExactlyOneHold() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Start timed out");
            try {
                service.hold(CarType.SEDAN, interval);
                return true;
            } catch (BookingException exception) {
                assertEquals(UNAVAILABLE, exception.reason(),
                "The losing concurrent request must report unavailable inventory");
                return false;
            }
        };
        try {
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);
            assertTrue(ready.await(5, TimeUnit.SECONDS),
                "Both competing requests must be ready before the concurrency check starts");
            start.countDown();
            assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS),
                "Exactly one concurrent request must acquire the last available car");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private static void assertReason(BookingException.Reason reason, Runnable operation, String requirement) {
        assertEquals(reason, assertThrows(BookingException.class, operation::run,
                requirement + ": operation must throw BookingException").reason(),
                requirement + ": failure reason must match");
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2030-01-01T10:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
    }
}
