package com.gateflow.payment_service.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

@Service
public class IdempotencyService {

    private static final Duration KEY_TTL =
            Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryStart(String key, String requestBody) {

        String redisKey = "idempotency:" + key;
        Boolean created = redisTemplate.opsForValue()
                .setIfAbsent(
                        redisKey,
                        "PROCESSING",
                        KEY_TTL
                );

        return Boolean.TRUE.equals(created);
    }

    public String getResult(String key) {
        return redisTemplate.opsForValue().get("idempotency:" + key);
    }

    public void storeResult(String key, String requestBody,String result) {

        String fingerprint = fingerprint(requestBody);
        String value = "COMPLETED:" + fingerprint + ":" + result;

        redisTemplate.opsForValue()
                .set(
                        "idempotency:" + key,
                        value,
                        KEY_TTL
                );
    }

    public boolean matchesRequest(String key, String requestBody) {

        String storedValue = redisTemplate.opsForValue().get("idempotency:" + key);

        if (storedValue == null) {
            return false;
        }

        String fingerprint = fingerprint(requestBody);
        return storedValue.contains(fingerprint);
    }

    private String fingerprint(String requestBody) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(requestBody.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();

            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        }

        catch (Exception e) {
            throw new IllegalStateException("Unable to generate request fingerprint", e);
        }
    }


}
