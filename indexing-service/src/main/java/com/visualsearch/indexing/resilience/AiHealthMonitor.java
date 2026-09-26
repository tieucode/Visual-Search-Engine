package com.visualsearch.indexing.resilience;

import com.visualsearch.indexing.client.ai.AiServiceClient;
import com.visualsearch.indexing.config.IndexingProperties;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Periodically probes the AI service when the circuit breaker is in HALF_OPEN state.
 *
 * <p>We implement {@link SchedulingConfigurer} instead of using
 * {@code @Scheduled(fixedDelayString)} because {@code @Scheduled} resolves the
 * property value as a raw string and only accepts plain millisecond integers or
 * ISO-8601 duration strings (e.g. "PT30S"). The Spring Boot shorthand "30s"
 * set in {@code application.yml} is handled by Boot's {@code DurationConverter}
 * when binding to {@link Duration} fields in {@code @ConfigurationProperties},
 * but that converter is NOT available inside the annotation processor that
 * parses {@code fixedDelayString}. By using {@code SchedulingConfigurer} we
 * receive an already-converted {@link Duration} from {@link IndexingProperties}
 * and schedule the task programmatically, side-stepping the issue entirely.
 */
@Component
public class AiHealthMonitor implements SchedulingConfigurer {

    private final AiServiceClient aiServiceClient;
    private final Duration healthCheckInterval;

    public AiHealthMonitor(AiServiceClient aiServiceClient,
                           IndexingProperties properties) {
        this.aiServiceClient = aiServiceClient;
        this.healthCheckInterval = properties.getAi().getHealthCheckInterval();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(
                new FixedDelayTask(this::probeWhenHalfOpen, healthCheckInterval, Duration.ZERO));
    }

    public void probeWhenHalfOpen() {
        aiServiceClient.probeReadinessIfDue();
    }
}
