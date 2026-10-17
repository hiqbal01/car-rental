package com.example.carrental.controller;

import com.example.carrental.service.ReservationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@Import(BookingControllerTest.ClockConfiguration.class)
class BookingControllerTest {
    private final List<UUID> createdHolds = new ArrayList<>();
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private ReservationService service;
    @Autowired private TestClock clock;

    @AfterEach
    void releaseTestHolds() {
        createdHolds.forEach(service::release);
        clock.now = Instant.parse("2030-01-01T00:00:00Z");
    }

    @Test
    void fullWorkflowSerializesFieldsAndKeepsConfirmedInventoryUnavailable() throws Exception {
        String date = "2030-02-01";
        MvcResult availability = mvc.perform(get("/availability").param("startDate", date)
                .param("days", "1")).andReturn();
        assertEquals(200, availability.getResponse().getStatus(), "Valid dates must allow availability browsing");
        assertEquals(2, body(availability).get("SEDAN").asInt(), "The configured fleet must expose two free sedans");
        JsonNode hold = createHold("{\"carType\":\"SEDAN\",\"startDate\":\"" + date + "\",\"days\":1}");
        assertFalse(hold.get("id").asText().isBlank(), "A hold response must expose its confirmation ID");
        assertFalse(hold.get("carId").asText().isBlank(), "A hold response must identify its assigned car");
        assertEquals("SEDAN", hold.get("carType").asText(), "The response must identify the requested car type");
        assertEquals("2030-02-01T15:00:00Z", hold.get("start").asText(), "Date-only pickup must default to 10 a.m. New York");
        assertEquals("2030-02-02T15:00:00Z", hold.get("end").asText(), "Days must calculate the local return date");
        assertEquals("2030-01-01T00:05:00Z", hold.get("expiresAt").asText(), "Hold expiry must use the clock plus the five-minute TTL");
        String url = "/holds/" + hold.get("id").asText();
        MvcResult confirmed = mvc.perform(post(url + "/confirm")).andReturn();
        assertEquals(200, confirmed.getResponse().getStatus(), "Active holds must be confirmable");
        JsonNode reservation = body(confirmed);
        assertEquals(hold.get("id"), reservation.get("holdId"), "Reservation responses must reference the original hold");
        assertEquals(hold.get("carId"), reservation.get("carId"), "Confirmation must preserve the assigned car");
        assertEquals(reservation, body(mvc.perform(post(url + "/confirm")).andReturn()),
                "Repeated HTTP confirmation must return the same reservation");
        assertEquals(204, mvc.perform(delete(url)).andReturn().getResponse().getStatus(),
                "Releasing a confirmed hold must be harmless");
        JsonNode remaining = body(mvc.perform(get("/availability").param("startDate", date).param("days", "1")).andReturn());
        assertEquals(1, remaining.get("SEDAN").asInt(), "Releasing a confirmed hold must not free its reservation");
        assertEquals(2, remaining.get("SUV").asInt(), "Sedan reservations must not consume SUV inventory");
        assertEquals(2, remaining.get("VAN").asInt(), "Sedan reservations must not consume van inventory");
    }

    @Test
    void snapshotDoesNotGuaranteeCapacityAndReleaseRestoresIt() throws Exception {
        String request = "{\"carType\":\"VAN\",\"startDate\":\"2030-03-01\",\"endDate\":\"2030-03-02\"}";
        JsonNode first = createHold(request);
        createHold(request);
        JsonNode counts = body(mvc.perform(get("/availability").param("startDate", "2030-03-01")
                .param("endDate", "2030-03-02")).andReturn());
        assertEquals(0, counts.get("VAN").asInt(), "Availability must include exhausted types with zero count");
        assertProblem(mvc.perform(post("/holds").contentType(MediaType.APPLICATION_JSON).content(request)).andReturn(),
                409, "UNAVAILABLE", "Hold creation must reject capacity consumed since browsing availability");
        String url = "/holds/" + first.get("id").asText();
        assertEquals(204, mvc.perform(delete(url)).andReturn().getResponse().getStatus(), "Release must succeed without a response body");
        assertEquals(204, mvc.perform(delete(url)).andReturn().getResponse().getStatus(), "Repeated release must remain successful");
        assertProblem(mvc.perform(post(url + "/confirm")).andReturn(), 404, "HOLD_NOT_FOUND", "Released holds must not be confirmable");
        createHold(request);
    }

    @Test
    void expiredHoldReturnsConflictAtTheTtlBoundary() throws Exception {
        JsonNode hold = createHold("""
                {"carType":"SUV","startDate":"2030-04-01","days":1}
                """);
        clock.now = clock.now.plusSeconds(300);
        assertProblem(mvc.perform(post("/holds/" + hold.get("id").asText() + "/confirm")).andReturn(),
                409, "HOLD_EXPIRED", "Confirmation must fail at the exact five-minute expiration boundary");
        JsonNode counts = body(mvc.perform(get("/availability").param("startDate", "2030-04-01")
                .param("days", "1")).andReturn());
        assertEquals(2, counts.get("SUV").asInt(), "Expired holds must restore inventory in availability responses");
    }

    @Test
    void invalidRequestsHaveConsistentBadRequestResponses() throws Exception {
        String[] requests = {
            "{}", "{\"startDate\":\"2030-01-02\",\"days\":1}",
            "{\"carType\":\"TRUCK\",\"startDate\":\"2030-01-02\",\"days\":1}",
            "{\"carType\":\"SEDAN\",\"startDate\":\"bad\",\"days\":1}",
            "{\"carType\":\"SEDAN\",\"startDate\":\"2030-01-02\",\"days\":0}",
            "{\"carType\":\"SEDAN\",\"startDate\":\"2030-01-02\",\"endDate\":\"2030-01-03\",\"days\":1}",
            "{\"carType\":\"SEDAN\",\"startDate\":\"2030-01-02\",\"endDate\":\"2030-01-01\"}",
            "{\"carType\":\"SEDAN\",\"startDate\":\"2030-03-10\",\"startTime\":\"02:30\",\"days\":1}",
            "{\"carType\":\"SEDAN\",\"startDate\":\"2030-11-03\",\"startTime\":\"01:30\",\"days\":1}"
        };
        for (String request : requests) {
            assertProblem(mvc.perform(post("/holds").contentType(MediaType.APPLICATION_JSON).content(request)).andReturn(),
                    400, "INVALID_REQUEST", "Invalid booking input must be rejected: " + request);
        }
        assertProblem(mvc.perform(get("/availability").param("startDate", "bad").param("days", "1")).andReturn(),
                400, "INVALID_REQUEST", "Malformed query dates must be rejected consistently");
        assertProblem(mvc.perform(get("/availability").param("days", "1")).andReturn(),
                400, "INVALID_REQUEST", "Availability browsing must require a start date");
        assertProblem(mvc.perform(post("/holds/not-a-uuid/confirm")).andReturn(),
                400, "INVALID_REQUEST", "Malformed hold IDs must produce a client error");
        assertProblem(mvc.perform(post("/holds/" + UUID.randomUUID() + "/confirm")).andReturn(),
                404, "HOLD_NOT_FOUND", "Unknown hold IDs must produce not found");
        assertProblem(mvc.perform(post("/holds").contentType(MediaType.APPLICATION_JSON)
                .content("{\"carType\":\"SEDAN\",\"startDate\":\"2029-01-01\",\"days\":1}")).andReturn(),
                400, "START_IN_PAST", "Bookings in the past must be rejected");
    }

    private JsonNode createHold(String request) throws Exception {
        MvcResult result = mvc.perform(post("/holds").contentType(MediaType.APPLICATION_JSON).content(request)).andReturn();
        assertEquals(201, result.getResponse().getStatus(), "A valid request with available inventory must create a hold: " + request);
        JsonNode hold = body(result);
        createdHolds.add(UUID.fromString(hold.get("id").asText()));
        return hold;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private void assertProblem(MvcResult result, int status, String code, String requirement) throws Exception {
        assertEquals(status, result.getResponse().getStatus(), requirement + ": HTTP status must match");
        assertEquals(code, body(result).get("code").asText(), requirement + ": error code must match");
        assertFalse(body(result).get("detail").asText().isBlank(), requirement + ": error detail must explain the failure");
    }

    static class TestClock extends Clock {
        private Instant now = Instant.parse("2030-01-01T00:00:00Z");

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }
}
