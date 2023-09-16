# round-robin-router
A Round Robin API which receives HTTP POSTS and routes them to one of a list of Application APIs.

The router will then receive the response from the application API and send it back to the client.

The list of application URLs can be specified before starting the server (it cannot be changed dynamically in current implementation).
If not specified, it uses a default hardcoded list.

The router also contains a simple Circuit Breaker to handle the cases when one of more application API is down or is answering too slowly.

## Circuit Breaker

TODO

## Usage

TODO

## Possible improvements 

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
  - TODO

### Circuit breaker 

- Use an existing library instead of doing it ourselves (e.g: https://resilience4j.readme.io/docs/circuitbreaker)
- After `RESET_TIMEOUT` is reached, the current implementation is directly closing the circuit. 
  It would be better, to implement a `HALF_OPEN` state, which permits a configurable number of calls to see if the backend is still unavailable or has become available again.
- Check if synchronisation can be reduced, with more optimistic locking using to Atomic variables.
- using Sliding Window TODO
  
### Other 
- If POST requests are idempotent (for example, by using a requestId), we can implement more retry mechanism.
- and possibly timeout
- retries when possible

### Application URLs configuration:
  - Use service discovery to add/remove servers
  - Use config to change server list (either with dynamic reload, or with restarting service)

  - TODO

### Production-ready improvements

- Implement monitoring. Most of the monitoring implementation can be out-of-the box if we use existing Circuit breaker libraries (e.g: resilience4j)
- JMeter load testing
- Improve logging

- TODO: timeout, it answer is too slow

Round robin: distribute requests to each replica in turn.
            Least loaded: maintain a count of outstanding requests to each replica, and distribute traffic to replicas
            with the smallest number of outstanding requests.

Peak EWMA: maintain a moving average of each replica’s round-trip time, weighted by the number of outstanding
requests, and distribute traffic to replicas where that cost function is smallest.





Adaptive Load Balancing: These algorithms continuously monitor server performance and adapt traffic
distribution based on real-time metrics such as server health, response times, or resource utilization.

Dynamic Weight Adjustment: Some load balancers can dynamically adjust server weights based on real-time
conditions, allowing for automatic load distribution based on server performance.

Weighted Round Robin: In this approach, each server is assigned a weight based on its capacity or performance.
 Servers with higher weights receive more requests than those with lower weights. This is useful when some
 servers are more powerful than others.




Adjust Round Robin Logic:

Modify your Round Robin logic to consider the detected slow responses. When selecting the next application API instance
to route a request, take into account the historical response times and the flagged slow instances.

For example, you could reduce the weight of slow instances in the round-robin rotation, making them less likely to receive
requests until their response times improve.
