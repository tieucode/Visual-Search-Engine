package com.visualsearch.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.visualsearch.config.SearchProperties;
import com.visualsearch.exception.SearchDependencyException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class QdrantSearchClient {
    private final RestClient client;
    private final String collection;

    public QdrantSearchClient(SearchProperties properties,
                               @Qualifier("qdrantSearchRestClient") RestClient client) {
        this.client = client;
        collection = properties.getQdrant().getCollection();
    }

    public List<ScoredImage> search(List<Float> vector, long offset, int limit) {
        return query(Map.of("query", vector, "offset", offset, "limit", limit,
                "with_payload", false, "with_vector", false), limit);
    }

    public List<ScoredImage> searchSimilar(UUID imageId, long offset, int limit) {
        return query(Map.of("query", imageId.toString(), "offset", offset, "limit", limit,
                "with_payload", false, "with_vector", false,
                "filter", Map.of("must_not", List.of(Map.of("has_id", List.of(imageId.toString()))))), limit);
    }

    public Map<UUID, List<Float>> getVectors(List<UUID> ids) {
        try {
            VectorResponse response = client.post().uri("/collections/{collection}/points", collection)
                    .body(Map.of("ids", ids, "with_payload", false, "with_vector", true))
                    .retrieve().body(VectorResponse.class);
            if (response == null || !"ok".equals(response.status()) || response.result() == null) {
                throw new SearchDependencyException("Qdrant returned invalid vectors");
            }
            Map<UUID, List<Float>> vectors = new HashMap<>();
            for (VectorPoint point : response.result()) {
                if (point != null && point.id() != null && point.vector() != null) {
                    vectors.put(point.id(), point.vector());
                }
            }
            return vectors;
        } catch (RestClientException exception) {
            throw new SearchDependencyException("Qdrant vector retrieval failed", exception);
        }
    }

    public List<List<ScoredImage>> searchBatch(List<float[]> vectors, List<UUID> excluded, int limit) {
        var queries = vectors.stream().map(vector -> Map.of(
                "query", vector, "limit", limit, "with_payload", false, "with_vector", false,
                "filter", Map.of("must_not", List.of(Map.of("has_id", excluded))))).toList();
        try {
            BatchQueryResponse response = client.post()
                    .uri("/collections/{collection}/points/query/batch", collection)
                    .body(Map.of("searches", queries))
                    .retrieve().body(BatchQueryResponse.class);
            if (response == null || !"ok".equals(response.status()) || response.result() == null
                    || response.result().size() != queries.size()) {
                throw new SearchDependencyException("Qdrant returned invalid batch search results");
            }
            return response.result().stream().map(result -> checkedPoints(result, limit)).toList();
        } catch (RestClientException exception) {
            throw new SearchDependencyException("Qdrant batch search failed", exception);
        }
    }

    private List<ScoredImage> query(Map<String, Object> request, int limit) {
        try {
            QueryResponse response = client.post().uri("/collections/{collection}/points/query", collection)
                    .body(request)
                    .retrieve().body(QueryResponse.class);
            if (response == null || !"ok".equals(response.status())) {
                throw new SearchDependencyException("Qdrant returned invalid search results");
            }
            return checkedPoints(response.result(), limit);
        } catch (RestClientException exception) {
            throw new SearchDependencyException("Qdrant search failed", exception);
        }
    }

    private List<ScoredImage> checkedPoints(QueryResult result, int limit) {
        if (result == null || result.points() == null || result.points().size() > limit
                || result.points().stream().anyMatch(point -> point == null || point.id() == null
                || point.score() == null || !Double.isFinite(point.score()))) {
            throw new SearchDependencyException("Qdrant returned invalid search results");
        }
        return result.points();
    }

    // The indexing worker uses the PostgreSQL image UUID as the Qdrant point ID.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoredImage(UUID id, Double score) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record QueryResult(List<ScoredImage> points) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record QueryResponse(String status, QueryResult result) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BatchQueryResponse(String status, List<QueryResult> result) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record VectorPoint(UUID id, List<Float> vector) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record VectorResponse(String status, List<VectorPoint> result) {}
}
