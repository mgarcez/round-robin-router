package com.maxime.router;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoundRobinRoutingTest {

    private final List<String> mockUrls = Arrays.asList(
            "http://api1.example.com",
            "http://api2.example.com",
            "http://api3.example.com"
    );

    @Test
    void testGetNextInstanceUrl() {
        RoundRobinRouting roundRobinRouting = new RoundRobinRouting(mockUrls);

        // Simulate four consecutive calls to getNextInstanceUrl
        String instance1 = roundRobinRouting.getNextInstanceUrl().getApplicationApiUrl();
        String instance2 = roundRobinRouting.getNextInstanceUrl().getApplicationApiUrl();
        String instance3 = roundRobinRouting.getNextInstanceUrl().getApplicationApiUrl();
        String instance4 = roundRobinRouting.getNextInstanceUrl().getApplicationApiUrl();

        // Verify that instances are returned in a round-robin fashion
        assertEquals("http://api1.example.com", instance1);
        assertEquals("http://api2.example.com", instance2);
        assertEquals("http://api3.example.com", instance3);
        assertEquals("http://api1.example.com", instance4);
    }

}