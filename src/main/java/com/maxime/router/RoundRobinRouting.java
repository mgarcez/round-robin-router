package com.maxime.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinRouting {

    private static final Logger logger = LoggerFactory.getLogger(RoundRobinRouting.class);
    private final AtomicInteger currentInstanceIndex = new AtomicInteger(0);

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

    public ApplicationApiInstance getNextInstanceUrl() {
        int nextIndex = currentInstanceIndex.getAndUpdate(
                (x) -> (x >= applicationApiInstances.size() - 1) ? 0 : x + 1
        );
        return applicationApiInstances.get(nextIndex);
    }

    public int getInstancesCount(){
        return applicationApiInstances.size();
    }
}
