package com.gateflow.payment_service;

import com.gateflow.payment_service.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IdempotencyServiceTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Test
    void shouldStoreAndReuseIdempotentResult() {

        String key = "test-" + UUID.randomUUID();

        String requestBody = """
                {
                    "userId": 1,
                    "amount": 100.00,
                    "currency": "INR"
                }
                """;

        String response = """
                {
                    "paymentId": "test-payment-123",
                    "status": "SUCCESS"
                }
                """;

        // First request should acquire the idempotency key
        assertTrue(idempotencyService.tryStart(key, requestBody));

        // Store the completed result
        idempotencyService.storeResult(
                key,
                requestBody,
                response
        );

        // Result should now be available
        String storedResult = idempotencyService.getResult(key);

        assertNotNull(storedResult);
        assertTrue(storedResult.startsWith("COMPLETED:"));

        // Same request should match the original fingerprint
        assertTrue(idempotencyService.matchesRequest(key, requestBody));

        // Same key with different request should NOT match
        String differentRequest = """
                {
                    "userId": 1,
                    "amount": 999.00,
                    "currency": "INR"
                }
                """;

        assertFalse(idempotencyService.matchesRequest(key, differentRequest)
        );
    }
}
