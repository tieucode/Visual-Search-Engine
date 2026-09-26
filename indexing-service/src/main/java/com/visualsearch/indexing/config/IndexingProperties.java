package com.visualsearch.indexing.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "indexing")
public class IndexingProperties {

    @Min(1)
    private int maxRetries = 3;

    @Valid
    @NotNull
    private Rabbit rabbit = new Rabbit();

    @Valid
    @NotNull
    private Image image = new Image();

    @Valid
    @NotNull
    private Ai ai = new Ai();

    @Valid
    @NotNull
    private Qdrant qdrant = new Qdrant();

    @Getter
    @Setter
    public static class Rabbit {
        @NotBlank
        private String exchange = "visual.search.exchange";
        @NotBlank
        private String queue = "image.uploaded.queue";
        @NotBlank
        private String routingKey = "image.uploaded";
        @NotBlank
        private String retryExchange = "visual.search.retry.exchange";
        @NotBlank
        private String retryQueue = "image.uploaded.retry.15m.queue";
        @NotBlank
        private String retryRoutingKey = "image.uploaded.retry";
        @NotBlank
        private String deadLetterExchange = "visual.search.dlq.exchange";
        @NotBlank
        private String deadLetterQueue = "image.uploaded.dlq";
        @NotBlank
        private String deadLetterRoutingKey = "image.uploaded.dlq";
        @NotNull
        private Duration retryDelay = Duration.ofMinutes(15);
        @NotNull
        private Duration publisherConfirmTimeout = Duration.ofSeconds(5);
        @NotNull
        private Duration shutdownTimeout = Duration.ofSeconds(60);
    }

    @Getter
    @Setter
    public static class Image {
        @Min(1)
        private long maxBytes = 20L * 1024 * 1024;
        @Min(1)
        private long maxPixels = 40_000_000L;
        @Min(224)
        private int maxDimension = 1024;
        @Min(1)
        @Max(100)
        private int maxBatchImages = 20;
        private List<String> allowedHosts = new ArrayList<>(List.of("minio", "localhost"));
        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(3);
        @NotNull
        private Duration requestTimeout = Duration.ofSeconds(30);
    }

    @Getter
    @Setter
    public static class Ai {
        @NotBlank
        private String baseUrl = "http://localhost:8000";
        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(3);
        @NotNull
        private Duration requestTimeout = Duration.ofSeconds(60);
        @NotNull
        private Duration healthCheckInterval = Duration.ofSeconds(30);
        @NotBlank
        private String healthPath = "/health";
        @Valid
        @NotNull
        private Circuit circuit = new Circuit();
    }

    @Getter
    @Setter
    public static class Circuit {
        @Min(1)
        private int failureThreshold = 5;
        @NotNull
        private Duration openDuration = Duration.ofSeconds(60);
    }

    @Getter
    @Setter
    public static class Qdrant {
        @NotBlank
        private String baseUrl = "http://localhost:6333";
        @NotBlank
        private String collection = "images";
        @Min(1)
        private int vectorSize = 768;
        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(3);
        @NotNull
        private Duration requestTimeout = Duration.ofSeconds(10);
    }
}
