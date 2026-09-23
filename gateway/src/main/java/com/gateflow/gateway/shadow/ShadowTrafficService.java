package com.gateflow.gateway.shadow;

import com.gateflow.gateway.metrics.GatewayMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ShadowTrafficService {

    private static final Logger logger = LoggerFactory.getLogger(ShadowTrafficService.class);

    private static final String SHADOW_TARGET = "http://localhost:8085";

    private static final Set<String> SHADOW_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final GatewayMetrics gatewayMetrics;

    private final HttpClient httpClient;

    public ShadowTrafficService(GatewayMetrics gatewayMetrics) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        this.gatewayMetrics = gatewayMetrics;
    }

    public void mirror(
            String method,
            String path,
            String queryString,
            Map<String, String> headers,
            String body
    ) {
        String shadowMethod = method.toUpperCase();

        boolean isPaymentRequest = path.startsWith("/payments");

        if (!isPaymentRequest) {
            return;
        }

        boolean isSafeMethod =
                Set.of("GET", "HEAD", "OPTIONS").contains(shadowMethod);

        if (!isSafeMethod && !shadowMethod.equals("POST")) {
            return;
        }

        gatewayMetrics.recordShadowRequest();

        String targetUrl = SHADOW_TARGET + path;

        if (queryString != null && !queryString.isEmpty()) {
            targetUrl += "?" + queryString;
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(2));

        if (shadowMethod.equals("POST") && isPaymentRequest) {

            requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(
                            body == null ? "" : body
                    ))
                    .header("X-Shadow-Request", "true");

        } else {

            requestBuilder.method(
                    shadowMethod,
                    HttpRequest.BodyPublishers.noBody()
            );
        }

        headers.forEach((name, value) -> {

            if (!name.equalsIgnoreCase("Host")
                    && !name.equalsIgnoreCase("Content-Length")
                    && !name.equalsIgnoreCase("Transfer-Encoding")
                    && !name.equalsIgnoreCase("Idempotency-Key")) {

                try {
                    requestBuilder.header(name, value);
                } catch (IllegalArgumentException ignored) {
                    // Ignore restricted headers.
                }
            }
        });

        final String shadowUrl = targetUrl;
        final HttpRequest shadowRequest = requestBuilder.build();

        Thread.startVirtualThread(() -> {
            try {

                HttpResponse<String> response =
                        httpClient.send(
                                shadowRequest,
                                HttpResponse.BodyHandlers.ofString()
                        );

                logger.info(
                        "Shadow request: {} {} -> {}",
                        shadowMethod,
                        shadowUrl,
                        response.statusCode()
                );

            } catch (Exception e) {

                gatewayMetrics.recordShadowFailure();

                logger.warn(
                        "Shadow request failed: {} {} - {}",
                        shadowMethod,
                        shadowUrl,
                        e.getMessage()
                );
            }
        });
    }
}
