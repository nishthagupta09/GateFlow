package com.gateflow.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class GatewayMetrics {

    private final Counter requests;
    private final Counter rateLimitRejections;
    private final Counter retries;
    private final Timer requestLatency;
    private final MeterRegistry registry;
    private final Counter shadowRequests;
    private final Counter shadowFailures;

    public GatewayMetrics(MeterRegistry registry) {

        this.registry = registry;

        requests = Counter.builder("gateflow_requests_total")
                .description("Total requests processed by GateFlow")
                .register(registry);

        rateLimitRejections = Counter.builder("gateflow_rate_limit_rejections_total")
                        .description("Requests rejected by rate limiting")
                        .register(registry);

        retries = Counter.builder("gateflow_retries_total")
                        .description("Total downstream retry attempts")
                        .register(registry);

        requestLatency = Timer.builder("gateflow_request_latency")
                        .description("GateFlow request processing latency")
                        .register(registry);

        shadowRequests = Counter.builder("gateflow_shadow_requests_total")
                        .description("Total requests sent to the shadow target")
                        .register(registry);

        shadowFailures = Counter.builder("gateflow_shadow_failures_total")
                        .description("Shadow requests that failed")
                        .register(registry);
    }

    public void recordRequest() {
        requests.increment();
    }

    public void recordRateLimitRejection() {
        rateLimitRejections.increment();
    }

    public void recordRetry() {
        retries.increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start();
    }

    public void recordLatency(Timer.Sample sample) {
        sample.stop(requestLatency);
    }

    public void recordShadowRequest() {
        shadowRequests.increment();
    }

    public void recordShadowFailure() {
        shadowFailures.increment();
    }

    public void recordDownstreamRequest(String target) {

        Counter.builder("gateflow_downstream_requests_total")
                .description("Requests routed to each downstream target")
                .tag("target", target)
                .register(registry)
                .increment();
    }
}