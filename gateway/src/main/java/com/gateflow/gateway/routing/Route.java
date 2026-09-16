package com.gateflow.gateway.routing;

import com.gateflow.gateway.config.RouteProperties;

import java.util.List;

public record Route
    (String prefix,
    List<RouteProperties.Target> targets) {}

