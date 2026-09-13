package com.gateflow.gateway.loadbalancing;

import com.gateflow.gateway.health.CircuitBreaker;
import com.gateflow.gateway.health.HealthChecker;
import org.springframework.stereotype.Component;

import java.util.List;
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

    public String choose(List<String> targets){

        List<String> availableTargets = targets.stream()
                .filter(healthChecker::isHealthy)
                .filter(circuitBreaker::allowRequest)
                .toList();


        if (availableTargets.isEmpty()) {
            throw new IllegalStateException(
                    "No healthy targets available"
            );
        }

        int index = Math.floorMod(
                counter.getAndIncrement(),
                availableTargets.size()
        );

        return availableTargets.get(index);
    }
}
