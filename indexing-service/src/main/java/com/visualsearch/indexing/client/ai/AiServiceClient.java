package com.visualsearch.indexing.client.ai;

import com.visualsearch.indexing.config.IndexingProperties;
import com.visualsearch.indexing.exception.AiCircuitOpenException;
import com.visualsearch.indexing.exception.AiServiceException;
import com.visualsearch.indexing.image.PreparedImage;
import com.visualsearch.indexing.resilience.AiCircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class AiServiceClient {

    private final RestClient restClient;
    private final AiCircuitBreaker circuitBreaker;
    private final String healthPath;

    public AiServiceClient(IndexingProperties properties, AiCircuitBreaker aiCircuitBreaker) {
        IndexingProperties.Ai ai = properties.getAi();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(ai.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(ai.getRequestTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(ai.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
        this.circuitBreaker = aiCircuitBreaker;
        this.healthPath = ai.getHealthPath();
    }

    public boolean isAvailableForBusiness() {
        return circuitBreaker.isAvailableForBusiness();
    }

    public AiBatchResponse index(List<PreparedImage> images, Runnable onCallAccepted) {
        if (images.isEmpty()) {
            return new AiBatchResponse(List.of());
        }
        if (!circuitBreaker.tryAcquireBusinessCall()) {
            log.info("AI request skipped because circuit is open imageCount={}", images.size());
            throw new AiCircuitOpenException();
        }

        long started = System.nanoTime();
        try {
            onCallAccepted.run();
        } catch (RuntimeException exception) {
            throw exception;
        }

        try {
            AiBatchResponse response = executeIndexRequest(images);
            if (response.results().isEmpty()) {
                throw new AiServiceException("AI service returned no result items", true);
            }
            boolean everyItemRetryableFailure = response.results().stream()
                    .allMatch(result -> !result.successful()
                            && result.error() != null
                            && result.error().isRetryable());
            if (everyItemRetryableFailure) {
                throw new AiServiceException("AI service reported a retryable failure for every image", true);
            }
            circuitBreaker.onBusinessSuccess();
            log.info("AI batch request succeeded imageCount={} resultCount={} durationMs={}",
                    images.size(),
                    response.results().size(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            return response;
        } catch (RuntimeException exception) {
            AiServiceException classified = classify(exception);
            circuitBreaker.onBusinessFailure(classified.isRetryable());
            log.warn("AI batch request failed imageCount={} retryable={} durationMs={} reason={}",
                    images.size(),
                    classified.isRetryable(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                    classified.getMessage());
            throw classified;
        }
    }

    public void probeReadinessIfDue() {
        if (!circuitBreaker.tryAcquireHealthProbe()) {
            return;
        }
        long started = System.nanoTime();
        try {
            restClient.get()
                    .uri(healthPath)
                    .retrieve()
                    .onStatus(status -> status.value() >= 400, (request, response) -> {
                        throw new AiServiceException(
                                "AI readiness returned HTTP " + response.getStatusCode().value(),
                                true);
                    })
                    .toBodilessEntity();
            circuitBreaker.onHealthProbeSuccess();
            log.info("AI health probe succeeded durationMs={}",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        } catch (RuntimeException exception) {
            AiServiceException classified = classify(exception);
            circuitBreaker.onHealthProbeFailure();
            log.warn("AI health probe failed durationMs={} reason={}",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                    classified.getMessage());
        }
    }

    private AiBatchResponse executeIndexRequest(List<PreparedImage> images) {
        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        for (PreparedImage image : images) {
            multipart.add("imageIds", image.imageId().toString());
            multipart.add("images", new NamedByteArrayResource(image.bytes(), image.imageId() + ".jpg"));
        }

        AiBatchResponse response = restClient.post()
                .uri("/api/v1/indexing/batch")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart)
                .retrieve()
                .onStatus(status -> status.value() >= 400, (request, httpResponse) -> {
                    int status = httpResponse.getStatusCode().value();
                    boolean retryable = status == 408 || status == 425 || status == 429 || status >= 500;
                    throw new AiServiceException("AI service returned HTTP " + status, retryable);
                })
                .body(AiBatchResponse.class);
        if (response == null) {
            throw new AiServiceException("AI service returned an empty response", true);
        }
        return response;
    }

    private AiServiceException classify(RuntimeException exception) {
        if (exception instanceof AiServiceException aiServiceException) {
            return aiServiceException;
        }
        if (exception instanceof ResourceAccessException) {
            return new AiServiceException("AI service connection failed", true, exception);
        }
        if (exception instanceof RestClientException) {
            return new AiServiceException("AI service request failed", true, exception);
        }
        return new AiServiceException("Unexpected AI service failure", true, exception);
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
