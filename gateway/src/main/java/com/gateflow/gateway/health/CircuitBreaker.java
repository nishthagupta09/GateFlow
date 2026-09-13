package com.gateflow.gateway.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CircuitBreaker {

    private static final int FAILURE_THRESHOLD = 3;

    private static final Duration RECOVERY_TIMEOUT = Duration.ofSeconds(10);

    private final Map<String, CircuitState> states = new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    public boolean allowRequest(String target) {

        CircuitState state = states.computeIfAbsent(target, key -> new CircuitState());
        synchronized (state) {
            if (state.status == Status.CLOSED) {
                return true;
            }

            if (state.status == Status.OPEN) {
                Duration elapsed = Duration.between(state.openedAt, Instant.now());

                if (elapsed.compareTo(RECOVERY_TIMEOUT) >= 0) {

                    state.status = Status.HALF_OPEN;
                    state.testRequestInProgress = true;

                    logger.info(
                            "Circuit HALF_OPEN for {} after recovery timeout",
                            target
                    );
                    return true;
                }
                return false;
            }

            if (!state.testRequestInProgress) {
                state.testRequestInProgress = true;
                return true;
            }
            return false;
        }
    }

    public void recordSuccess(String target) {

        CircuitState state = states.computeIfAbsent(target, key -> new CircuitState());

        synchronized (state) {
            if (state.status == Status.HALF_OPEN) {
                logger.info("Circuit CLOSED for {} after successful recovery request", target);
            }

            state.failureCount = 0;
            state.status = Status.CLOSED;
            state.testRequestInProgress = false;
        }
    }

    public void recordFailure(String target) {

        CircuitState state = states.computeIfAbsent(target, key -> new CircuitState());

        synchronized (state) {

            state.failureCount++;
            state.testRequestInProgress = false;

            if (state.failureCount >= FAILURE_THRESHOLD && state.status != Status.OPEN) {
                state.status = Status.OPEN;
                state.openedAt = Instant.now();

                logger.warn(
                        "Circuit OPEN for {} after {} consecutive failures",
                        target,
                        state.failureCount
                );
            }
        }
    }

    public String getState(String target) {

        CircuitState state = states.get(target);

        if (state == null) {
            return Status.CLOSED.name();
        }

        synchronized (state) {
            return state.status.name();
        }
    }

    private enum Status {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private static class CircuitState {

        private Status status = Status.CLOSED;

        private int failureCount = 0;

        private Instant openedAt;

        private boolean testRequestInProgress = false;
    }
}