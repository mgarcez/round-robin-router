package com.maxime.router;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Basic Circuit Breaker.
 *
 * The circuit opens when the number of consecutive reported errors reach CONSECUTIVE_FAILURE_THRESHOLD.
 *
 * The circuit closes again after waiting RESET_TIMEOUT_MS from the last failure.
 *
 * There is no half-open mechanism implemented in this implementation.
 */
public class CircuitBreaker {
    /**
     * Number of consecutive failures before triggering the circuit
     */
    static final int CONSECUTIVE_FAILURE_THRESHOLD = 3;
    /**
     * Time to wait before attempting to reset the circuit (in milliseconds)
     */
    static final int RESET_TIMEOUT_MS = 5000;
    private final AtomicInteger consecutiveFailuresCount = new AtomicInteger(0);

    private long lastFailureTimestamp = 0;

    /**
     * Acquire permission.
     *
     * Checks that the circuit is closed (i.e: circuit is already closed, or RESET_TIMEOUT_MS is reached)
     *
     * @return if the circuit is closed (and thus calls are allowed)
     */
    public synchronized boolean acquirePermission() {
        // Circuit is closed, allow requests
        if(isClosed()){
            return true;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFailureTimestamp >= RESET_TIMEOUT_MS) {
            // Reset the circuit when the timeout is reached
            consecutiveFailuresCount.set(0);
            return true;
        } else {
            // Circuit is open, don't allow requests
            return false;
        }
    }

    private boolean isClosed() {
        return consecutiveFailuresCount.get() < CONSECUTIVE_FAILURE_THRESHOLD;
    }

    /**
     * Report a failed call
     * It increments the consecutiveFailuresCount.
     * Opens the Circuit breaker if consecutiveFailuresCount reached the FAILURE_THRESHOLD.
     */
    public synchronized void reportFailure() {
        lastFailureTimestamp = System.currentTimeMillis();
        consecutiveFailuresCount.incrementAndGet();
    }


    /**
     * Report successful call.
     * It resets consecutiveFailuresCount to 0.
     */
    public synchronized void reportSuccess() {
        consecutiveFailuresCount.set(0);
    }
}
