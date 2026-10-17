package com.example.carrental.controller;

import com.example.carrental.exception.BookingException;
import java.time.DateTimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(BookingException.class)
    public ProblemDetail bookingFailure(BookingException exception) {
        HttpStatus status = switch (exception.reason()) {
            case HOLD_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case START_IN_PAST -> HttpStatus.BAD_REQUEST;
            case HOLD_EXPIRED, UNAVAILABLE -> HttpStatus.CONFLICT;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setProperty("code", exception.reason().name());
        return problem;
    }

    @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class})
    public ProblemDetail invalidPeriod(RuntimeException exception) {
        return invalidRequest(exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validationFailure(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted().reduce((first, next) -> first + "; " + next).orElse("Invalid request");
        return invalidRequest(detail);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail malformedInput(Exception exception) {
        return invalidRequest("Malformed input: use ISO dates/times, a valid UUID, and SEDAN, SUV, or VAN");
    }

    private ProblemDetail invalidRequest(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("code", "INVALID_REQUEST");
        return problem;
    }
}
