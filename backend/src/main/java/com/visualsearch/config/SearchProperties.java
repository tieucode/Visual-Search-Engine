package com.visualsearch.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.search")
public class SearchProperties {
    @Min(1)
    private int maxImageBytes = 10 * 1024 * 1024;
    @Min(1)
    private long maxImagePixels = 40_000_000;
    @Valid
    private Ai ai = new Ai();
    @Valid
    private Qdrant qdrant = new Qdrant();

    @Getter
    @Setter
    public static class Ai {
        @NotBlank
        private String baseUrl = "http://localhost:8000";
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration requestTimeout = Duration.ofSeconds(60);
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
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration requestTimeout = Duration.ofSeconds(10);
    }
}
