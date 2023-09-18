package com.maxime.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RouterController {

    private static final Logger logger = LoggerFactory.getLogger(RouterController.class);

    static final int SLOW_CALL_DURATION_THRESHOLD_MS = 5000;

    private final RestTemplate restTemplate;

    private final RoundRobinRouting roundRobinRouting;

    public RouterController() {
        this(Arrays.asList(
                "http://localhost:5001/api/endpoint",
                "http://localhost:5002/api/endpoint",
                "http://localhost:5003/api/endpoint")
        );
    }

    RouterController(List<String> applicationApiUrls) {
        this(applicationApiUrls, new RestTemplate());
    }

    RouterController(List<String> applicationApiUrls, RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        roundRobinRouting = new RoundRobinRouting(applicationApiUrls);
    }

    @PostMapping("/router")
    public ResponseEntity<Object> routeRequest(@RequestBody Map<String, Object> requestData) {

        // Try to send the request to an instance with no open circuit
        for (int i = 0; i < roundRobinRouting.getInstancesCount(); i++) {
            ApplicationApiInstance applicationApiInstance = roundRobinRouting.getNextInstanceUrl();
            if (applicationApiInstance.acquirePermission()) {
                return sendRequest(requestData, applicationApiInstance);
            } else {
                logger.warn("Downstream server " + applicationApiInstance.getApplicationApiUrl()
                        + " is skipped because it has its circuit open.");
            }
        }

        // Handle the case when no healthy instance is available
        logger.warn("No healthy Application API instances available. The request was not processed.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("No healthy Application API instances available. The request was not processed. You can retry again later.");
    }

    private ResponseEntity<Object> sendRequest(Map<String, Object> requestData, ApplicationApiInstance applicationApiInstance) {
        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<Object> response = restTemplate.postForEntity(
                    applicationApiInstance.getApplicationApiUrl(),
                    requestData,
                    Object.class
            );
            long endTime = System.currentTimeMillis();
            if (isSlowCall(startTime, endTime)) { //TODO use callback instead, to report failure faster
                logger.warn("Downstream server " + applicationApiInstance.getApplicationApiUrl()
                        + " took too long to answer: " + (endTime - startTime) + " MS.");
                applicationApiInstance.reportFailure();
            } else {
                if (isServerError(response)) {
                    logger.warn("Downstream server " + applicationApiInstance.getApplicationApiUrl()
                            + " returned an HTTP error: " + response.getStatusCode());
                    applicationApiInstance.reportFailure();
                } else {
                    applicationApiInstance.reportSuccess();
                }
            }

            return response;
        } catch (ResourceAccessException resourceAccessException) {
            if (resourceAccessException.getCause() instanceof ConnectException) {
                logger.warn("Downstream server down: " + applicationApiInstance.getApplicationApiUrl());
                applicationApiInstance.reportFailure();
            }
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("The downstream server is down. You can retry again."); //TODO The router should retry with another instance
        } catch (Exception e) {
            logger.error("Error processing the request for server: " + applicationApiInstance.getApplicationApiUrl(), e);
            applicationApiInstance.reportFailure();
            throw e;
        }
    }


    private static boolean isSlowCall(long startTime, long endTime) {
        return endTime - startTime > SLOW_CALL_DURATION_THRESHOLD_MS;
    }

    private static boolean isServerError(ResponseEntity<Object> response) {
        return response.getStatusCode().is5xxServerError()
                || response.getStatusCode() == HttpStatus.REQUEST_TIMEOUT
                || response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
    }
}
