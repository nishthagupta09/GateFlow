package com.gateflow.gateway.routing;

import java.util.List;

public record Route
    (String prefix,
    List<String> targets) {}

