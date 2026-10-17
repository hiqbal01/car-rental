package com.example.carrental.controller;

import com.example.carrental.domain.CarType;
import com.example.carrental.dto.BookingRequest;
import com.example.carrental.dto.HoldResponse;
import com.example.carrental.dto.ReservationResponse;
import com.example.carrental.service.BookingTimeResolver;
import com.example.carrental.service.ReservationService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {
    private final ReservationService service;
    private final BookingTimeResolver timeResolver;

    public BookingController(ReservationService service, BookingTimeResolver timeResolver) {
        this.service = service;
        this.timeResolver = timeResolver;
    }

    @GetMapping("/availability")
    public Map<CarType, Long> availability(@Valid @ModelAttribute BookingRequest request) {
        return service.availability(request.toInterval(timeResolver));
    }

    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse hold(@Valid @RequestBody BookingRequest request) {
        if (request.getCarType() == null) throw new IllegalArgumentException("Car type is required");
        return new HoldResponse(service.hold(request.getCarType(), request.toInterval(timeResolver)));
    }

    @PostMapping("/holds/{holdId}/confirm")
    public ReservationResponse confirm(@PathVariable UUID holdId) {
        return new ReservationResponse(service.confirm(holdId));
    }

    @DeleteMapping("/holds/{holdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable UUID holdId) {
        service.release(holdId);
    }
}
