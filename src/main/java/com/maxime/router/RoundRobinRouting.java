package com.maxime.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RoundRobinRouting {

    private static final Logger logger = LoggerFactory.getLogger(RoundRobinRouting.class);
    private int currentInstanceIndex = -1;

    private final List<ApplicationApiInstance> applicationApiInstances;

    public RoundRobinRouting(List<String> applicationApiUrls){
        logger.info("Starting Round Robin Router with following Urls: " + applicationApiUrls);

        if (applicationApiUrls == null || applicationApiUrls.size() == 0) {
            throw new IllegalArgumentException("Cannot start a router with a null or empty Application API Urls list.");
        }

        applicationApiInstances = new ArrayList<>(applicationApiUrls.size());
        for (String applicationApiUrl : applicationApiUrls) {
            applicationApiInstances.add(new ApplicationApiInstance(applicationApiUrl));
        }
    }

    public synchronized ApplicationApiInstance getNextInstanceUrl() {
        for (int i = 0; i < applicationApiInstances.size(); i++) {
            currentInstanceIndex = (currentInstanceIndex >= applicationApiInstances.size() -1) ? 0 : currentInstanceIndex + 1;
            ApplicationApiInstance nextInstance = applicationApiInstances.get(currentInstanceIndex);
            if(nextInstance.acquirePermission()){
                return nextInstance;
            }
            logger.debug("Downstream server " + nextInstance.getApplicationApiUrl() + " is skipped because it has its circuit open.");
        }
        return null;
    }
}
