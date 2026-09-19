package com.gateflow.gateway.proxy;

import com.gateflow.gateway.metrics.GatewayMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.apache.logging.log4j.util.StringBuilders.equalsIgnoreCase;
import static org.springframework.http.HttpMethod.POST;

@Component
public class ProxyClient {

    private final HttpClient httpClient;
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_DELAY_MS = 100;
    private static final Logger logger = LoggerFactory.getLogger(ProxyClient.class);
    private final GatewayMetrics gatewayMetrics;

    public ProxyClient(GatewayMetrics gatewayMetrics){   //constructor
        this.httpClient=HttpClient.newHttpClient();
        this.gatewayMetrics=gatewayMetrics;
    }

    public HttpResponse<String> forward(
            String method,
            String targetUrl,
            String body,
            Map<String, String> headers
    ) throws Exception {

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(2));

            HttpRequest.BodyPublisher bodyPublisher;

            if (body == null || body.isEmpty()) {
                bodyPublisher = HttpRequest.BodyPublishers.noBody();
            } else {
                bodyPublisher = HttpRequest.BodyPublishers.ofString(body);
            }

            builder.method(method, bodyPublisher);

            for (Map.Entry<String, String> header : headers.entrySet()) {

                String headerName = header.getKey();

                if (headerName.equalsIgnoreCase("Connection")
                        || headerName.equalsIgnoreCase("Host")
                        || headerName.equalsIgnoreCase("Content-Length")
                        || headerName.equalsIgnoreCase("Transfer-Encoding")
                        || headerName.equalsIgnoreCase("Upgrade")
                        || headerName.equalsIgnoreCase("Expect")){
                    continue;
                }

                builder.header(headerName, header.getValue());
            }

            HttpRequest request = builder.build();

            logger.info(
                    "Attempt {} → {} {}",
                    attempt,
                    method,
                    targetUrl
            );

            HttpResponse<String> response;

            try {

                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                logger.info(
                        "Attempt {} → downstream returned {}",
                        attempt,
                        response.statusCode()
                );

            }
            catch (Exception e) {
                logger.warn("Attempt {} → downstream request failed: {}", attempt, e.getClass().getSimpleName());

                if (attempt == MAX_ATTEMPTS || !isRetryableMethod(method, headers)) {
                    throw e;
                }

                long exponentialDelay = INITIAL_DELAY_MS * (1L << (attempt - 1));

                long jitter = ThreadLocalRandom.current()
                                .nextLong(0, 51);

                long delay = exponentialDelay + jitter;

                gatewayMetrics.recordRetry();

                logger.info("Retrying after {} ms", delay);
                Thread.sleep(delay);
                continue;
            }

            if (response.statusCode() < 500 || attempt == MAX_ATTEMPTS || !isRetryableMethod(method,headers)) {
                return response;
            }

            long exponentialDelay = INITIAL_DELAY_MS * (1L << (attempt - 1));

            long jitter = ThreadLocalRandom.current().nextLong(0, 51);

            long delay = exponentialDelay + jitter;

            gatewayMetrics.recordRetry();

            logger.info("Retrying after {} ms", delay);
            Thread.sleep(delay);
        }
        throw new IllegalStateException("Request failed after retries");
    }

    private boolean isRetryableMethod(String method, Map<String, String> headers) {
        if (method.equalsIgnoreCase("GET")
                || method.equalsIgnoreCase("HEAD")
                || method.equalsIgnoreCase("OPTIONS")) {
            return true;
        }

        if (method.equalsIgnoreCase("POST")) {
            return headers.keySet().stream()
                    .anyMatch(header -> header.equalsIgnoreCase("Idempotency-Key"));
        }
        return false;
    }
}
