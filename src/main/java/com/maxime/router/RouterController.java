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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api")
public class RouterController {

    private static final Logger logger = LoggerFactory.getLogger(RouterController.class);

    private static final int SLOW_CALL_DURATION_THRESHOLD_MS = 5000;

    private final AtomicInteger currentInstanceIndex = new AtomicInteger(0);

    private final List<ApplicationApiInstance> applicationApiInstances;

    private final RestTemplate restTemplate;

    public RouterController() {
        this(Arrays.asList(
                "http://localhost:5000/api/endpoint",
                "http://localhost:5001/api/endpoint",
                "http://localhost:5002/api/endpoint")
        );
    }

    RouterController(List<String> applicationApiUrls) {
        this(applicationApiUrls, new RestTemplate());
    }

    RouterController(List<String> applicationApiUrls, RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        if (applicationApiUrls == null || applicationApiUrls.size() == 0) {
            throw new IllegalArgumentException("Cannot start a router with a null or empty Application API Urls list.");
        }

        applicationApiInstances = new ArrayList<>(applicationApiUrls.size());
        for (String applicationApiUrl : applicationApiUrls) {
            applicationApiInstances.add(new ApplicationApiInstance(applicationApiUrl));
        }

        logger.info("Starting Router with following Urls: " + applicationApiUrls);
    }

    @PostMapping("/router")
    public ResponseEntity<Object> routeRequest(@RequestBody Map<String, Object> requestData) {

        for (int i = 0; i < applicationApiInstances.size(); i++) {
            ApplicationApiInstance applicationApiInstance = getNextInstanceUrl();
            if (applicationApiInstance.acquirePermission()) {
                return sendRequest(requestData, applicationApiInstance);
            }
            else{
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
            if (isSlowCall(startTime, endTime)) {
                logger.warn("Downstream server " + applicationApiInstance.getApplicationApiUrl()
                        + " took too long to answer: " + (endTime - startTime) + " MS.");
                applicationApiInstance.reportFailure();
            } else {
                applicationApiInstance.reportSuccess();
            }
            if (isServerError(response)) {
                logger.warn("Downstream server " + applicationApiInstance.getApplicationApiUrl()
                        + " returned an HTTP error: " + response.getStatusCode());
                applicationApiInstance.reportFailure();
            }

            return response;
        } catch (ResourceAccessException resourceAccessException) {
            if (resourceAccessException.getCause() instanceof ConnectException) {
                logger.warn("Downstream server down: " + applicationApiInstance.getApplicationApiUrl());
                applicationApiInstance.reportFailure();
            }
            throw resourceAccessException;
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

    ApplicationApiInstance getNextInstanceUrl() {
        int nextIndex = currentInstanceIndex.getAndUpdate(
                (x) -> (x >= applicationApiInstances.size() - 1) ? 0 : x + 1
        );
        return applicationApiInstances.get(nextIndex);
    }
}