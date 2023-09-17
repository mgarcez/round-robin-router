package com.maxime.router;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SpringBootTest
class RouterControllerTest {
    private final List<String> mockUrls = Arrays.asList(
            "http://api1.example.com",
            "http://api2.example.com",
            "http://api3.example.com"
    );

    @Test
    void testGetNextInstanceUrl() {
        RouterController routerController = new RouterController(mockUrls);

        // Simulate four consecutive calls to getNextInstanceUrl
        String instance1 = routerController.getNextInstanceUrl().getApplicationApiUrl();
        String instance2 = routerController.getNextInstanceUrl().getApplicationApiUrl();
        String instance3 = routerController.getNextInstanceUrl().getApplicationApiUrl();
        String instance4 = routerController.getNextInstanceUrl().getApplicationApiUrl();

        // Verify that instances are returned in a round-robin fashion
        assertEquals("http://api1.example.com", instance1);
        assertEquals("http://api2.example.com", instance2);
        assertEquals("http://api3.example.com", instance3);
        assertEquals("http://api1.example.com", instance4);
    }

    @Test
    void testWithEmptyConstructor() {
        RouterController routerController = new RouterController();

        // Simulate four consecutive calls to getNextInstanceUrl
        String instance1 = routerController.getNextInstanceUrl().getApplicationApiUrl();
        String instance2 = routerController.getNextInstanceUrl().getApplicationApiUrl();
        String instance3 = routerController.getNextInstanceUrl().getApplicationApiUrl();
        String instance4 = routerController.getNextInstanceUrl().getApplicationApiUrl();

        // Verify that instances are returned in a round-robin fashion
        // And using the default URLs
        assertEquals("http://localhost:5001/api/endpoint", instance1);
        assertEquals("http://localhost:5002/api/endpoint", instance2);
        assertEquals("http://localhost:5003/api/endpoint", instance3);
        assertEquals("http://localhost:5001/api/endpoint", instance4);
    }

    @Test
    void testRouteRequestWithCircuitBreaker() {
        RestTemplate mockedRestTemplate = Mockito.mock(RestTemplate.class);
        List<String> mockUrl2 = Arrays.asList(
                "http://api1.example.com",
                "http://api2.example.com"
        );
        RouterController routerController = new RouterController(mockUrl2, mockedRestTemplate);
        ResponseEntity<Object> response;

        ResourceAccessException resourceAccessExceptionWithConnectException = new ResourceAccessException("", new ConnectException());

        // request1 send to api1: SERVER DOWN
        when(mockedRestTemplate.postForEntity("http://api1.example.com", Collections.singletonMap("data", "request1"), Object.class))
                .thenThrow(resourceAccessExceptionWithConnectException);
        response = routerController.routeRequest(Collections.singletonMap("data", "request1"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());

        // request2 send to api2: SUCCESS
        when(mockedRestTemplate.postForEntity("http://api2.example.com", Collections.singletonMap("data", "request2"), Object.class))
                .thenReturn(new ResponseEntity<Object>(Collections.singletonMap("data", "request2"), HttpStatus.OK));
        response = routerController.routeRequest(Collections.singletonMap("data", "request2"));
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // request3 send to api1: 500
        when(mockedRestTemplate.postForEntity("http://api1.example.com", Collections.singletonMap("data", "request3"), Object.class))
                .thenReturn(new ResponseEntity<Object>(Collections.singletonMap("data", "request3"), HttpStatus.INTERNAL_SERVER_ERROR));
        response = routerController.routeRequest(Collections.singletonMap("data", "request3"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        // request4 send to api2: SUCCESS
        when(mockedRestTemplate.postForEntity("http://api2.example.com", Collections.singletonMap("data", "request4"), Object.class))
                .thenReturn(new ResponseEntity<Object>(Collections.singletonMap("data", "request4"), HttpStatus.OK));
        response = routerController.routeRequest(Collections.singletonMap("data", "request4"));
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // request5 send to api1: RESPONSE_TOO_SLOW
        when(mockedRestTemplate.postForEntity("http://api1.example.com", Collections.singletonMap("data", "request5"), Object.class))
                .thenAnswer(
                        new AnswersWithDelay(
                                RouterController.SLOW_CALL_DURATION_THRESHOLD_MS + 100,
                                new Returns(new ResponseEntity<Object>(Collections.singletonMap("data", "request5"), HttpStatus.OK))
                        )
                );
        response = routerController.routeRequest(Collections.singletonMap("data", "request5"));
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Circuit Breaker should be opened now for api1, because of 3 consecutive errors

        // request send to api2: 500
        // All requests should go to api2, because api1 is open
        for (int i = 0; i < CircuitBreaker.CONSECUTIVE_FAILURE_THRESHOLD; i++) {
            //System.out.println(i);
            when(mockedRestTemplate.postForEntity("http://api2.example.com", Collections.singletonMap("data", "request"), Object.class))
                    .thenReturn(new ResponseEntity<Object>(Collections.singletonMap("data", "request"), HttpStatus.INTERNAL_SERVER_ERROR));
            response = routerController.routeRequest(Collections.singletonMap("data", "request"));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        }

        // Both api1 and api2 circuit breaker should be open
        when(mockedRestTemplate.postForEntity("http://api2.example.com", Collections.singletonMap("data", "request"), Object.class))
                .thenReturn(new ResponseEntity<Object>(Collections.singletonMap("data", "request"), HttpStatus.INTERNAL_SERVER_ERROR));
        response = routerController.routeRequest(Collections.singletonMap("data", "request"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(
                "No healthy Application API instances available. The request was not processed. You can retry again later.",
                response.getBody()
        );
    }
}
