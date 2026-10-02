package com.visualsearch.indexing.resilience;

import com.visualsearch.indexing.config.IndexingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class AiCircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openedAt;
    private boolean healthProbeInFlight;

    @Autowired
    public AiCircuitBreaker(IndexingProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AiCircuitBreaker(IndexingProperties properties, Clock clock) {
        IndexingProperties.Circuit circuit = properties.getAi().getCircuit();
        this.failureThreshold = circuit.getFailureThreshold();
        this.openDuration = circuit.getOpenDuration();
        this.clock = clock;
    }

    public synchronized boolean isAvailableForBusiness() {
        return state == State.CLOSED;
    }

    public synchronized boolean tryAcquireBusinessCall() {
        return state == State.CLOSED;
    }

    public synchronized void onBusinessSuccess() {
        close();
    }

    public synchronized void onBusinessFailure(boolean retryable) {
        if (!retryable) {
            close();
            return;
        }

        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            open();
        }
    }

    public synchronized boolean tryAcquireHealthProbe() {
        if (state == State.OPEN
                && openedAt != null
                && !clock.instant().isBefore(openedAt.plus(openDuration))) {
            state = State.HALF_OPEN;
        }
        if (state != State.HALF_OPEN || healthProbeInFlight) {
            return false;
        }
        healthProbeInFlight = true;
        return true;
    }

    public synchronized void onHealthProbeSuccess() {
        close();
    }

    public synchronized void onHealthProbeFailure() {
        open();
    }

    public synchronized State state() {
        return state;
    }

    private void open() {
        state = State.OPEN;
        openedAt = clock.instant();
        healthProbeInFlight = false;
    }

    private void close() {
        state = State.CLOSED;
        consecutiveFailures = 0;
        openedAt = null;
        healthProbeInFlight = false;
    }
}
