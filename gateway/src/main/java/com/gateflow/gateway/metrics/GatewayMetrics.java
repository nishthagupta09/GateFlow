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

    public GatewayMetrics(MeterRegistry registry) {

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
}