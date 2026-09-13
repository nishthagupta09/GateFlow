package com.gateflow.gateway.health;

import com.gateflow.gateway.config.RouteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HealthChecker {

    private final RouteProperties routeProperties;

    private final HttpClient httpClient;

    private final Map<String, Boolean> healthStatus =
            new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(HealthChecker.class);

    public HealthChecker(RouteProperties routeProperties) {
        this.routeProperties = routeProperties;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public boolean isHealthy(String target) {
        return healthStatus.getOrDefault(target, false);
    }

    public void checkHealth() {

        for (RouteProperties.RouteDefinition route
                : routeProperties.getRoutes()) {

            List<String> targets = route.getTargets();

            for (String target : targets) {

                boolean healthy = checkTarget(target);

                healthStatus.put(target, healthy);

                logger.info("Health check: {} -> {}",
                        target,
                        healthy ? "HEALTHY" : "UNHEALTHY");
            }
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 0)
    public void scheduledHealthCheck() {
        checkHealth();
    }

    private boolean checkTarget(String target) {

        try {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target + "/health"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            return response.statusCode() >= 200
                    && response.statusCode() < 300;

        } catch (Exception e) {

            return false;
        }
    }

}
