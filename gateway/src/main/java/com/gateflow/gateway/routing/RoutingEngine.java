package com.gateflow.gateway.routing;

import com.gateflow.gateway.config.RouteProperties;
import org.springframework.stereotype.Component;

@Component
public class RoutingEngine {

    private final RouteProperties routeProperties;

    public RoutingEngine(RouteProperties routeProperties) {
        this.routeProperties = routeProperties;
    }

    public Route findRoute(String path) {

        for (RouteProperties.RouteDefinition route
                : routeProperties.getRoutes()) {

            String routePrefix = route.getPath().replace("/**", "");

            if (path.startsWith(routePrefix) || path.startsWith(routePrefix + "/")) {
                return new Route(
                        routePrefix,
                        route.getTargets()
                );
            }
        }

        throw new IllegalArgumentException(
                "No route found for: " + path
        );
    }

}
