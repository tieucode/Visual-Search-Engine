package com.visualsearch.indexing.client.qdrant;

import com.visualsearch.indexing.config.IndexingProperties;
import com.visualsearch.indexing.exception.QdrantOperationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class QdrantIndexClient {

    private final RestClient restClient;
    private final IndexingProperties.Qdrant properties;
    private final AtomicBoolean collectionReady = new AtomicBoolean(false);

    public QdrantIndexClient(IndexingProperties indexingProperties) {
        this.properties = indexingProperties.getQdrant();
        this.restClient = com.visualsearch.indexing.config.DependencyHttpClients.create(
                properties.getBaseUrl(), properties.getConnectTimeout(), properties.getRequestTimeout());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeCollection() {
        try {
            ensureCollection();
        } catch (QdrantOperationException exception) {
            log.warn("Qdrant collection is not ready at startup: {}", exception.getMessage());
        }
    }

    public record Point(UUID imageId, UUID batchId, List<Float> embedding) {}

    public Map<UUID, QdrantOperationException> upsertBatch(List<Point> points) {
        Map<UUID, QdrantOperationException> failures = new LinkedHashMap<>();
        List<Point> valid = new java.util.ArrayList<>();
        for (Point point : points) {
            if (point.embedding() == null || point.embedding().size() != properties.getVectorSize()
                    || point.embedding().stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
                failures.put(point.imageId(), new QdrantOperationException("Invalid embedding values or dimension", false));
            } else valid.add(point);
        }
        if (valid.isEmpty()) return failures;
        try {
            sendPoints(valid);
        } catch (QdrantOperationException failure) {
            if (failure.isRetryable() || valid.size() == 1) {
                valid.forEach(point -> failures.put(point.imageId(), failure));
            } else {
                for (Point point : valid) {
                    try { sendPoints(List.of(point)); }
                    catch (QdrantOperationException itemFailure) { failures.put(point.imageId(), itemFailure); }
                }
            }
        }
        log.info("Qdrant batch completed imageCount={} failed={}", points.size(), failures.size());
        return failures;
    }

    private Map<String, Object> payload(Point point) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("imageId", point.imageId().toString());
        if (point.batchId() != null) payload.put("batchId", point.batchId().toString());
        return Map.of("id", point.imageId().toString(), "vector", point.embedding(), "payload", payload);
    }

    private void sendPoints(List<Point> points) {
        ensureCollection();
        try {
            restClient.put()
                    .uri("/collections/{collection}/points?wait=true", properties.getCollection())
                    .body(Map.of("points", points.stream().map(this::payload).toList()))
                    .retrieve()
                    .onStatus(status -> status.value() >= 400, (request, response) -> {
                        throw classifyStatus(response.getStatusCode().value(), "upsert point");
                    })
                    .toBodilessEntity();
        } catch (QdrantOperationException exception) {
            if (exception.isRetryable()) {
                collectionReady.set(false);
            }
            throw exception;
        } catch (ResourceAccessException exception) {
            collectionReady.set(false);
            throw new QdrantOperationException("Qdrant connection failed", true, exception);
        } catch (RestClientException exception) {
            collectionReady.set(false);
            throw new QdrantOperationException("Qdrant request failed", true, exception);
        }
    }

    public void ensureCollection() {
        if (collectionReady.get()) return;
        synchronized (collectionReady) {
            if (!collectionReady.get()) inspectCollection();
        }
    }

    private void inspectCollection() {
        try {
            restClient.get()
                    .uri("/collections/{collection}", properties.getCollection())
                    .retrieve()
                    .toBodilessEntity();
            collectionReady.set(true);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 404) {
                throw classifyStatus(exception.getStatusCode().value(), "inspect collection");
            }
            createCollection();
        } catch (ResourceAccessException exception) {
            throw new QdrantOperationException("Qdrant connection failed", true, exception);
        } catch (RestClientException exception) {
            throw new QdrantOperationException("Could not inspect Qdrant collection", true, exception);
        }
    }

    private void createCollection() {
        try {
            restClient.put()
                    .uri("/collections/{collection}", properties.getCollection())
                    .body(Map.of("vectors", Map.of(
                            "size", properties.getVectorSize(),
                            "distance", "Cosine")))
                    .retrieve()
                    .onStatus(status -> status.value() >= 400, (request, response) -> {
                        if (response.getStatusCode().value() != 409) {
                            throw classifyStatus(response.getStatusCode().value(), "create collection");
                        }
                    })
                    .toBodilessEntity();
            collectionReady.set(true);
            log.info("Qdrant collection '{}' is ready", properties.getCollection());
        } catch (QdrantOperationException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new QdrantOperationException("Qdrant connection failed", true, exception);
        } catch (RestClientException exception) {
            throw new QdrantOperationException("Could not create Qdrant collection", true, exception);
        }
    }

    private QdrantOperationException classifyStatus(int status, String operation) {
        boolean retryable = status == 404 || status == 408 || status == 425 || status == 429 || status >= 500;
        return new QdrantOperationException(
                "Qdrant returned HTTP " + status + " while attempting to " + operation,
                retryable);
    }
}
