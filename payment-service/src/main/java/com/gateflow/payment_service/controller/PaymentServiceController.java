package com.gateflow.payment_service.controller;

import com.gateflow.payment_service.service.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentServiceController {

    private final IdempotencyService idempotencyService;
    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceController.class);

    public PaymentServiceController(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Value("${server.port}")
    private int port;

    @PostMapping
    public ResponseEntity<String> createPayment(  HttpServletRequest httpRequest,
                                                  @RequestHeader(value = "Idempotency-Key", required = false)
                                                  String idempotencyKey,
                                                  @RequestBody PaymentRequest request) {

        String requestId = httpRequest.getHeader("X-Request-ID");
        logger.info("[{}] Payment request received",
                requestId);

        System.out.println("Payment request received by port: " + port);

        String requestBody = """
                {
                    "userId": %d,
                    "amount": %.2f,
                    "currency": "%s"
                }
                """.formatted(
                request.userId(),
                request.amount(),
                request.currency()
        );

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {

            String existing = idempotencyService.getResult(idempotencyKey);

            if (existing != null) {
                if (!idempotencyService.matchesRequest(idempotencyKey, requestBody)) {
                    return ResponseEntity
                            .status(409)
                            .body("""
                                    {
                                        "error": "Idempotency key was already used with a different request"
                                    }
                                    """);
                }

                if (existing.startsWith("COMPLETED:")) {

                    String previousResult = existing.substring(
                            existing.indexOf(":", "COMPLETED:".length()) + 1);
                    return ResponseEntity.ok(previousResult);
                }

                if (existing.startsWith("PROCESSING:")) {

                    return ResponseEntity
                            .status(409)
                            .body("""
                                    {
                                        "error": "Request is already being processed"
                                    }
                                    """);
                }
            }

            boolean started = idempotencyService.tryStart(idempotencyKey, requestBody);

            if (!started) {
                return ResponseEntity
                        .status(409)
                        .body("""
                                {
                                    "error": "Request is already being processed"
                                }
                                """);
            }
        }

        String paymentId = UUID.randomUUID().toString();

        String response = """
                {
                    "paymentId": "%s",
                    "userId": %d,
                    "amount": %.2f,
                    "currency": "%s",
                    "status": "SUCCESS",
                    "service": "payment-service",
                    "port": %d
                }
                """.formatted(
                paymentId,
                request.userId(),
                request.amount(),
                request.currency(),
                port
        );

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.storeResult(idempotencyKey, requestBody, response);
        }
        return ResponseEntity.ok(response);
    }

    public record PaymentRequest(
            Long userId,
            Double amount,
            String currency
    ) {}
}