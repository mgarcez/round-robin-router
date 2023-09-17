package com.maxime.router;

public class ApplicationApiInstance {

    private final String applicationApiUrl;
    private final CircuitBreaker circuitBreaker;

    public ApplicationApiInstance(String applicationApiUrl) {
        this.applicationApiUrl = applicationApiUrl;
        this.circuitBreaker = new CircuitBreaker();
    }

    public String getApplicationApiUrl() {
        return applicationApiUrl;
    }

    public boolean acquirePermission() {
        return circuitBreaker.acquirePermission();
    }

    public void reportFailure() {
        circuitBreaker.reportFailure();
    }

    public void reportSuccess() {
        circuitBreaker.reportSuccess();
    }
}
