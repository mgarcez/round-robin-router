package com.maxime.router;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = new CircuitBreaker();
    }

    @Test
    void testCircuitStartsClosed() {
        assertTrue(circuitBreaker.acquirePermission());
    }

    @Test
    void testCircuitOpensAfterFailures() {
        assertTrue(circuitBreaker.acquirePermission());

        // Simulate consecutive failures
        for (int i = 0; i < CircuitBreaker.CONSECUTIVE_FAILURE_THRESHOLD - 1; i++) {
            circuitBreaker.reportFailure();
            assertTrue(circuitBreaker.acquirePermission());
        }

        // After reaching FAILURE_THRESHOLD, the circuit should be open
        circuitBreaker.reportFailure();
        assertFalse(circuitBreaker.acquirePermission());
    }

    @Test
    void testCircuitResetsAfterTimeout() throws InterruptedException {
        assertTrue(circuitBreaker.acquirePermission());

        // Simulate consecutive failures
        for (int i = 0; i < CircuitBreaker.CONSECUTIVE_FAILURE_THRESHOLD - 1; i++) {
            circuitBreaker.reportFailure();
            assertTrue(circuitBreaker.acquirePermission());
        }

        // After reaching FAILURE_THRESHOLD, the circuit should be open
        circuitBreaker.reportFailure();
        assertFalse(circuitBreaker.acquirePermission());

        // Wait for the circuit breaker to reset (RESET_TIMEOUT)
        Thread.sleep(CircuitBreaker.RESET_TIMEOUT_MS + 100);


        // The circuit should be closed again
        assertTrue(circuitBreaker.acquirePermission());
        assertTrue(circuitBreaker.acquirePermission());
    }

    @Test
    void testCircuitDoesNotResetBeforeTimeout() throws InterruptedException {
        assertTrue(circuitBreaker.acquirePermission());

        // Simulate consecutive failures
        for (int i = 0; i < CircuitBreaker.CONSECUTIVE_FAILURE_THRESHOLD - 1; i++) {
            circuitBreaker.reportFailure();
            assertTrue(circuitBreaker.acquirePermission());
        }

        // After reaching FAILURE_THRESHOLD, the circuit should be open
        circuitBreaker.reportFailure();
        assertFalse(circuitBreaker.acquirePermission());

        // Wait for a shorter time than RESET_TIMEOUT
        Thread.sleep(CircuitBreaker.RESET_TIMEOUT_MS - 100);

        // The circuit should still be open
        assertFalse(circuitBreaker.acquirePermission());
    }

    @Test
    void testCircuitIsResetAfterSuccessfulCall() {
        assertTrue(circuitBreaker.acquirePermission());

        circuitBreaker.reportFailure();
        assertTrue(circuitBreaker.acquirePermission());
        circuitBreaker.reportFailure();
        assertTrue(circuitBreaker.acquirePermission());

        // Reset consecutiveFailuresCount when  reportSuccess
        circuitBreaker.reportSuccess();
        assertTrue(circuitBreaker.acquirePermission());


        circuitBreaker.reportFailure();
        assertTrue(circuitBreaker.acquirePermission());
        circuitBreaker.reportFailure();
        assertTrue(circuitBreaker.acquirePermission());

        // After reaching FAILURE_THRESHOLD, the circuit should be open
        circuitBreaker.reportFailure();
        assertFalse(circuitBreaker.acquirePermission());
    }

    //TODO
    // test several closes
}
