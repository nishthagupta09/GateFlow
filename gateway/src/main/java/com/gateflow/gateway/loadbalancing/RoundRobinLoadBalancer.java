package com.gateflow.gateway.loadbalancing;

import com.gateflow.gateway.config.RouteProperties;
import com.gateflow.gateway.health.CircuitBreaker;
import com.gateflow.gateway.health.HealthChecker;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RoundRobinLoadBalancer {

    private final AtomicInteger counter=new AtomicInteger(0);
    private final HealthChecker healthChecker;
    private final CircuitBreaker circuitBreaker;

    public RoundRobinLoadBalancer(HealthChecker healthChecker, CircuitBreaker circuitBreaker) {
        this.healthChecker = healthChecker;
        this.circuitBreaker=circuitBreaker;
    }

    public String choose(List<RouteProperties.Target> targets){

        List<RouteProperties.Target> availableTargets = targets.stream()
                .filter(target-> healthChecker.isHealthy(target.getUrl()))
                .filter(target-> circuitBreaker.allowRequest(target.getUrl()))
                .toList();


        if (availableTargets.isEmpty()) {
            throw new IllegalStateException("No healthy targets available");
        }

        List<RouteProperties.Target> weightedTargets = new ArrayList<>();

        for (RouteProperties.Target target : availableTargets) {

            for (int i = 0; i < target.getWeight(); i++) {
                weightedTargets.add(target);
            }
        }

        if (weightedTargets.isEmpty()) {
            throw new IllegalStateException("No targets with valid weights");
        }

        int index = ThreadLocalRandom.current().nextInt(weightedTargets.size());

        return weightedTargets.get(index).getUrl();
    }
}
