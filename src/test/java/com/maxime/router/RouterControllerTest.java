package com.maxime.router;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.util.Arrays;
import java.util.List;

import static com.fasterxml.jackson.databind.type.LogicalType.Map;
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
		assertEquals("http://localhost:5000/api/endpoint", instance1);
		assertEquals("http://localhost:5001/api/endpoint", instance2);
		assertEquals("http://localhost:5002/api/endpoint", instance3);
		assertEquals("http://localhost:5000/api/endpoint", instance4);
	}

	@Test
	void testGetNextInstanceUrlWithMocking() { //TODO remove

		RouterController mockedRouter = Mockito.mock(RouterController.class);

		when(mockedRouter.getNextInstanceUrl())
				.thenReturn( new ApplicationApiInstance("http://mocked-api.example.com"));

		// Test the behavior of the mocked object
		String instanceUrl = mockedRouter.getNextInstanceUrl().getApplicationApiUrl();

		// Verify that the mock returned the expected URL
		assertEquals("http://mocked-api.example.com", instanceUrl);
	}

	@Test
	void testRouteRequestWithCircuitBreaker() {
		RestTemplate mockedRestTemplate = Mockito.mock(RestTemplate.class);
		RouterController routerController = new RouterController(mockUrls, mockedRestTemplate);

		// Simulate consecutive failures to trigger the circuit breaker
		when(mockedRestTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(), Mockito.eq(Object.class)))
				.thenThrow(new ConnectException("Connect issue"))
				.thenThrow(new ConnectException("Connect issue"))
				.thenReturn(new ResponseEntity<>(Map.of("key", "value"), HttpStatus.OK));

		// First two requests should trigger the circuit breaker
		ResponseEntity<Object> response1 = routerController.routeRequestWithCircuitBreaker(
				Map.of("data", "request1"), 1, 3);
		ResponseEntity<Object> response2 = routerController.routeRequestWithCircuitBreaker(
				Map.of("data", "request2"), 1, 3);

		// Circuit breaker should be open, and these requests should fail
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response1.getStatusCode());
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response2.getStatusCode());

		// Wait for the circuit breaker to reset (RESET_TIMEOUT)
		try {
			Thread.sleep(6000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// Third request should succeed, and the circuit breaker should reset
		ResponseEntity<Object> response3 = routerController.routeRequestWithCircuitBreaker(
				Map.of("data", "request3"), 1, 3);
		assertEquals(HttpStatus.OK, response3.getStatusCode());
	}

}
