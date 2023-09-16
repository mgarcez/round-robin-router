# round-robin-router
A Round Robin API which receives HTTP POSTS and routes them to one of a list of Application APIs.

The router will then receive the response from the application API and send it back to the client.

The list of application URLs can be specified before starting the server (it cannot be changed dynamically in current implementation).
If not specified, it uses a default hardcoded list.

The router also contains a simple Circuit Breaker to handle the cases when one of more application API is down or is answering too slowly.

## Circuit Breaker

The Circuit Breaker implemented here is very basic:
- The circuit opens when the number of consecutive reported errors reach `CONSECUTIVE_FAILURE_THRESHOLD`.
- The circuit closes again after waiting `RESET_TIMEOUT_MS` from the last failure.
- There is no half-open mechanism implemented in this implementation. 
- Slow requests (above `SLOW_CALL_DURATION_THRESHOLD_MS`) are simply reported as a failure to the circuit breaker.

## Usage

Step 1: Start several instances of Application API servers

Step 2: Start the router
```shell
# Compile
./mvnw clean install

# Start the server
./mvnw spring-boot:run
```

Step 3: Send POST requests
```shell
# Example
curl -X POST -H "Content-Type: application/json" -d '{
  "game": "Mobile Legends",
  "gamerID": "GYUTDTE",
  "points": 20
}' http://localhost:8080/api/router
```

## Possible improvements 

To sum-up, the most important improvements would be:
- Improve throughput with a reactive framework (e.g `Vert.x`)
- Improve timeout mechanism
- use an existing Circuit Breaker library (e.g `resilience4j`), including features such as:
  - HALF_OPEN
  - differentiate between slow requests and failures
  - use sliding windows
- Optional: use Round Robin with a Dynamic Weight Adjustment 

More details on the improvements below:

### Throughput 

With the current implementation, the default Spring Boot container is used: Tomcat. 
The maximum number of threads Tomcat sets by default is `200` 
(this configuration can be changed in `application.properties` using `server.tomcat.max-threads`)

Calls to the Application APIs are done synchronously. 
Hence, the router throughput will start to be impacted once it reaches `server.tomcat.max-threads` requests, 
especially if the Application APIs take time to answer.

The logic throughput improvement is to use asynchronous calls.

Possible solutions include:
- Use Spring `AsyncRestTemplate` instead of its synchronous counter-part `RestTemplate`. 
However, from Spring 5.0, `AsyncRestTemplate` is being deprecated in favour of Spring Reactive.
- Use Spring Reactive framework (`WebFlux`)
- Use another reactive framework. 
One very good candidate for this router use-case would be `Vert.x` (or `Quarkus`, which uses `Vert.x`)

### Slow servers handling
  - With current implementation, slow requests (above `SLOW_CALL_DURATION_THRESHOLD_MS`) are simply reported as a failure to the circuit breaker.
It would be better to differentiate failures and slow requests at circuit breaker level.
  - Please also see `Timeout` section below regarding limitations on the matter.
  - Adjust Round Robin Logic with `Dynamic Weight Adjustment`: reduce the weight of slow instances in the round-robin rotation, making them less likely to receive
requests until their response times improve.

### Circuit breaker 

- Use an existing library instead of doing it ourselves (e.g: https://resilience4j.readme.io/docs/circuitbreaker)
- After `RESET_TIMEOUT` is reached, the current implementation is directly closing the circuit. 
  It would be better, to implement a `HALF_OPEN` state, which permits a configurable number of calls to see if the backend is still unavailable or has become available again.
- Check if synchronisation can be reduced, with more optimistic locking using Atomic variables.
- Use a Sliding Window instead of counting the number of consecutive errors
  
### Timeout 
- Implement timeout to better handler requests taking too long. 
(However, depending on the use case, it may be better to still continue waiting if requests cannot be retried because of
idempotency issues).
- Report failure to the Circuit breaker as soon as `SLOW_CALL_DURATION_THRESHOLD_MS` is reached.
At the moment, the application is still waiting for the call to finish before reporting the failure. This can be very inefficient.

### Retry
- Be more fined-grained on detecting retryable errors. And potentially retry them directly at router level.
- If POST requests are idempotent (for example, by using a requestId), we can implement even more retry mechanisms.

### Configuration

Make most Circuit Breaker variables configurable (`SLOW_CALL_DURATION_THRESHOLD_MS`, `RESET_TIMEOUT`, ...) using
a configuration file. Or even better, a configuration server.

Application URLs configuration:
  - Use service discovery to dynamically add/remove servers
  - OR use config server to change server list (either with dynamic reload, or by restarting the router)

### Production-ready improvements

- Monitoring. Most of the monitoring implementation can be out-of-the box if we use existing Circuit breaker libraries (e.g: resilience4j)
- Perform load testing
- More testing
- Review logging. Potentially, implement tracing.

