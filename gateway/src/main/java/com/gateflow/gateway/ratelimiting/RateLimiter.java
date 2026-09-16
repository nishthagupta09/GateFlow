package com.gateflow.gateway.ratelimiting;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    @Value("${rate-limit.capacity}")
    private int capacity;

    @Value("${rate-limit.window-seconds}")
    private int windowSeconds;

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local current = redis.call('INCR', KEYS[1])
                    
                    if current == 1 then
                        redis.call('EXPIRE', KEYS[1], ARGV[1])
                    end
                    
                    return current
                    """,
                    Long.class
            );

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String clientId) {

        String key = "rate-limit:" + clientId;

        Long requestCount = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(windowSeconds)
        );

        return requestCount != null && requestCount <= capacity;
    }
}