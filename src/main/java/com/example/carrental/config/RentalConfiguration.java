package com.example.carrental.config;

import com.example.carrental.domain.Car;
import com.example.carrental.domain.CarType;
import com.example.carrental.service.BookingTimeResolver;
import com.example.carrental.service.ReservationService;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RentalConfiguration {
    @Bean
    public BookingTimeResolver bookingTimeResolver(@Value("${rental.zone}") String zone) {
        return new BookingTimeResolver(ZoneId.of(zone));
    }

    @Bean
    public ReservationService reservationService(Clock clock,
            @Value("${rental.hold-ttl}") Duration ttl,
            @Value("${rental.fleet.sedans}") int sedans,
            @Value("${rental.fleet.suvs}") int suvs,
            @Value("${rental.fleet.vans}") int vans) {
        var fleet = new ArrayList<Car>();
        var counts = Map.of(CarType.SEDAN, sedans, CarType.SUV, suvs, CarType.VAN, vans);
        for (CarType type : CarType.values()) {
            int count = counts.get(type);
            if (count < 0) throw new IllegalArgumentException("Fleet counts must not be negative");
            for (int i = 1; i <= count; i++) fleet.add(new Car(type + "-" + i, type));
        }
        return new ReservationService(fleet, clock, ttl);
    }
}
