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
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getRequestTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeCollection() {
        try {
            ensureCollection();
        } catch (QdrantOperationException exception) {
            log.warn("Qdrant collection is not ready at startup: {}", exception.getMessage());
        }
    }

    public void upsert(UUID imageId, UUID batchId, List<Float> embedding) {
        if (embedding == null || embedding.size() != properties.getVectorSize()) {
            throw new QdrantOperationException(
                    "Embedding dimension must be " + properties.getVectorSize() + " but was "
                            + (embedding == null ? 0 : embedding.size()),
                    false);
        }
        ensureCollection();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("imageId", imageId.toString());
        if (batchId != null) {
            payload.put("batchId", batchId.toString());
        }
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", imageId.toString());
        point.put("vector", embedding);
        point.put("payload", payload);

        try {
            restClient.put()
                    .uri("/collections/{collection}/points?wait=true", properties.getCollection())
                    .body(Map.of("points", List.of(point)))
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

    public synchronized void ensureCollection() {
        if (collectionReady.get()) {
            return;
        }
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
