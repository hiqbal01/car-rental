package com.example.carrental;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class CarRentalApplicationTest {
    @Autowired
    private Clock clock;

    @Test
    void applicationStartsWithClockBean() {
        assertNotNull(clock.instant(),
                "The application must provide a working Clock bean for time-dependent booking rules");
    }
}
