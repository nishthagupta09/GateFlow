package com.gateflow.gateway;

import com.gateflow.gateway.health.CircuitBreaker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    @Test
    void shouldOpenCircuitAfterThreeFailures() {

        CircuitBreaker circuitBreaker = new CircuitBreaker();

        String target = "http://localhost:8082";

        // Circuit starts CLOSED
        assertTrue(circuitBreaker.allowRequest(target));
        assertEquals("CLOSED", circuitBreaker.getState(target));

        // First failure
        circuitBreaker.recordFailure(target);
        assertTrue(circuitBreaker.allowRequest(target));
        assertEquals("CLOSED", circuitBreaker.getState(target));

        // Second failure
        circuitBreaker.recordFailure(target);
        assertTrue(circuitBreaker.allowRequest(target));
        assertEquals("CLOSED", circuitBreaker.getState(target));

        // Third failure should OPEN the circuit
        circuitBreaker.recordFailure(target);

        assertEquals("OPEN", circuitBreaker.getState(target));
        assertFalse(circuitBreaker.allowRequest(target));
    }
}