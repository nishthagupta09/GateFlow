package com.gateflow.gateway.controller;

import com.gateflow.gateway.health.CircuitBreaker;
import com.gateflow.gateway.loadbalancing.RoundRobinLoadBalancer;
import com.gateflow.gateway.metrics.GatewayMetrics;
import com.gateflow.gateway.proxy.ProxyClient;
import com.gateflow.gateway.routing.Route;
import com.gateflow.gateway.routing.RoutingEngine;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class GatewayController {

    private final RoutingEngine routingEngine;
    private final ProxyClient proxyClient;
    private final RoundRobinLoadBalancer loadBalancer;
    private final CircuitBreaker circuitBreaker;
    private final GatewayMetrics gatewayMetrics;

    public GatewayController(RoutingEngine routingEngine,  ProxyClient proxyClient, RoundRobinLoadBalancer loadBalancer, CircuitBreaker circuitBreaker, GatewayMetrics gatewayMetrics) {
        this.routingEngine = routingEngine;
        this.proxyClient= proxyClient;
        this.loadBalancer=loadBalancer;
        this.circuitBreaker=circuitBreaker;
        this.gatewayMetrics=gatewayMetrics;
    }

    @GetMapping("/health")
    public String health() {
        return "GateFlow is running";
    }

    @RequestMapping("/**")
    public ResponseEntity<String> proxy(HttpServletRequest request,
                        @RequestBody(required = false) String body) throws Exception {

        Timer.Sample timer = gatewayMetrics.startTimer();

        String path = request.getRequestURI();

        Route route = routingEngine.findRoute(path);

        String downstreamPath = path.substring(4);

        String target = loadBalancer.choose(route.targets());

        String targetUrl = target + downstreamPath;

        String queryString = request.getQueryString();

        if (queryString != null && !queryString.isEmpty()) {
            targetUrl += "?" + queryString;
        }

        Map<String, String> headers = new HashMap<>();

        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {

            String headerName = headerNames.nextElement();

            headers.put(
                    headerName,
                    request.getHeader(headerName)
            );
        }

        String requestId = (String) request.getAttribute("X-Request-ID");

        headers.put("X-Request-ID", requestId);

        try {

            HttpResponse<String> response =
                    proxyClient.forward(
                            request.getMethod(),
                            targetUrl,
                            body,
                            headers
                    );

            if (response.statusCode() < 500) {
                circuitBreaker.recordSuccess(target);
            }
            else {
                circuitBreaker.recordFailure(target);
            }


            return ResponseEntity
                    .status(response.statusCode())
                    .body(response.body());

        }
        catch (HttpTimeoutException e) {

            circuitBreaker.recordFailure(target);

            return ResponseEntity
                    .status(504)
                    .body("""
                            {
                                "error": "Gateway Timeout",
                                "message": "Downstream service did not respond within the timeout"
                            }
                            """);
        }
        finally {
            gatewayMetrics.recordLatency(timer);
        }
    }
}