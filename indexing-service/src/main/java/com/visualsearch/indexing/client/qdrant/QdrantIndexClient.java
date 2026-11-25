package com.visualsearch.indexing.client.qdrant;

import com.visualsearch.indexing.config.DependencyHttpClients;
import com.visualsearch.indexing.config.IndexingProperties;
import com.visualsearch.indexing.exception.QdrantOperationException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
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
    private final AtomicBoolean clipsReady = new AtomicBoolean(false);

    public QdrantIndexClient(IndexingProperties indexingProperties) {
        this.properties = indexingProperties.getQdrant();
        this.restClient =
                DependencyHttpClients.create(
                        properties.getBaseUrl(),
                        properties.getConnectTimeout(),
                        properties.getRequestTimeout());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeCollection() {
        try {
            ensureCollection();
            ensureCollection(clipCollection(), clipsReady, true);
        } catch (QdrantOperationException exception) {
            log.warn("Qdrant collection is not ready at startup: {}", exception.getMessage());
        }
    }

    public record Point(UUID imageId, UUID batchId, List<Float> embedding) {}

    public Map<UUID, QdrantOperationException> upsertBatch(List<Point> points) {
        Map<UUID, QdrantOperationException> failures = new LinkedHashMap<>();
        List<Point> valid = new ArrayList<>();
        for (Point point : points) {
            if (!validEmbedding(point.embedding())) {
                failures.put(
                        point.imageId(),
                        new QdrantOperationException(
                                "Invalid embedding values or dimension", false));
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
                    try {
                        sendPoints(List.of(point));
                    } catch (QdrantOperationException itemFailure) {
                        failures.put(point.imageId(), itemFailure);
                    }
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
        return Map.of(
                "id", point.imageId().toString(), "vector", point.embedding(), "payload", payload);
    }

    private void sendPoints(List<Point> points) {
        ensureCollection();
        sendPoints(properties.getCollection(), collectionReady, points.stream().map(this::payload).toList());
    }

    public void upsertClip(UUID id, List<List<Float>> vectors, List<Integer> timestamps) {
        if (vectors.isEmpty() || vectors.size() != timestamps.size()
                || vectors.stream().anyMatch(vector -> !validEmbedding(vector))) {
            throw new QdrantOperationException("Invalid clip embeddings", false);
        }
        String collection = clipCollection();
        ensureCollection(collection, clipsReady, true);
        sendPoints(collection, clipsReady, List.of(Map.of(
                "id", id, "vector", vectors, "payload", Map.of("frameTimesMs", timestamps))));
    }

    private boolean validEmbedding(List<Float> vector) {
        return vector != null && vector.size() == properties.getVectorSize()
                && vector.stream().allMatch(value -> value != null && Float.isFinite(value));
    }

    private String clipCollection() {
        return properties.getCollection() + "_clips";
    }

    private void sendPoints(String collection, AtomicBoolean ready, List<Map<String, Object>> points) {
        try {
            restClient
                    .put()
                    .uri("/collections/{collection}/points?wait=true", collection)
                    .body(Map.of("points", points))
                    .retrieve()
                    .onStatus(
                            status -> status.value() >= 400,
                            (request, response) -> {
                                throw classifyStatus(
                                        response.getStatusCode().value(), "upsert point");
                            })
                    .toBodilessEntity();
        } catch (QdrantOperationException exception) {
            if (exception.isRetryable()) {
                ready.set(false);
            }
            throw exception;
        } catch (ResourceAccessException exception) {
            ready.set(false);
            throw new QdrantOperationException("Qdrant connection failed", true, exception);
        } catch (RestClientException exception) {
            ready.set(false);
            throw new QdrantOperationException("Qdrant request failed", true, exception);
        }
    }

    public void ensureCollection() {
        ensureCollection(properties.getCollection(), collectionReady, false);
    }

    private void ensureCollection(String collection, AtomicBoolean ready, boolean clips) {
        if (ready.get()) return;
        synchronized (ready) {
            if (!ready.get()) inspectCollection(collection, ready, clips);
        }
    }

    private void inspectCollection(String collection, AtomicBoolean ready, boolean clips) {
        try {
            restClient
                    .get()
                    .uri("/collections/{collection}", collection)
                    .retrieve()
                    .toBodilessEntity();
            ready.set(true);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 404) {
                throw classifyStatus(exception.getStatusCode().value(), "inspect collection");
            }
            createCollection(collection, ready, clips);
        } catch (ResourceAccessException exception) {
            throw new QdrantOperationException("Qdrant connection failed", true, exception);
        } catch (RestClientException exception) {
            throw new QdrantOperationException(
                    "Could not inspect Qdrant collection", true, exception);
        }
    }

    private void createCollection(String collection, AtomicBoolean ready, boolean clips) {
        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("size", properties.getVectorSize());
        vectors.put("distance", "Cosine");
        if (clips) {
            vectors.put("multivector_config", Map.of("comparator", "max_sim"));
            vectors.put("hnsw_config", Map.of("m", 0));
        }
        try {
            restClient
                    .put()
                    .uri("/collections/{collection}", collection)
                    .body(Map.of("vectors", vectors))
                    .retrieve()
                    .onStatus(
                            status -> status.value() >= 400,
                            (request, response) -> {
                                if (response.getStatusCode().value() != 409) {
                                    throw classifyStatus(
                                            response.getStatusCode().value(), "create collection");
                                }
                            })
                    .toBodilessEntity();
            ready.set(true);
            log.info("Qdrant collection '{}' is ready", collection);
        } catch (QdrantOperationException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new QdrantOperationException("Qdrant connection failed", true, exception);
        } catch (RestClientException exception) {
            throw new QdrantOperationException(
                    "Could not create Qdrant collection", true, exception);
        }
    }

    private QdrantOperationException classifyStatus(int status, String operation) {
        boolean retryable =
                status == 404 || status == 408 || status == 425 || status == 429 || status >= 500;
        return new QdrantOperationException(
                "Qdrant returned HTTP " + status + " while attempting to " + operation, retryable);
    }
}
