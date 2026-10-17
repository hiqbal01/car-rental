package com.example.carrental.exception;

public class BookingException extends RuntimeException {
    private final Reason reason;

    public enum Reason { UNAVAILABLE, HOLD_NOT_FOUND, HOLD_EXPIRED, START_IN_PAST }

    public BookingException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
