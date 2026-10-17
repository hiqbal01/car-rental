package com.example.carrental.dto;

import com.example.carrental.domain.BookingInterval;
import com.example.carrental.domain.CarType;
import com.example.carrental.service.BookingTimeResolver;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

/** Mutable only for Spring query binding and JSON deserialization. */
public class BookingRequest {
    private CarType carType;
    @NotNull(message = "Start date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime startTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime endTime;
    private Integer days;

    public CarType getCarType() { return carType; }

    public void setCarType(CarType carType) { this.carType = carType; }

    public LocalDate getStartDate() { return startDate; }

    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalTime getStartTime() { return startTime; }

    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalDate getEndDate() { return endDate; }

    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public LocalTime getEndTime() { return endTime; }

    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public Integer getDays() { return days; }

    public void setDays(Integer days) { this.days = days; }

    public BookingInterval toInterval(BookingTimeResolver resolver) {
        return resolver.resolve(startDate, startTime, endDate, endTime, days);
    }
}
