package com.visualsearch.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.visualsearch.config.SearchProperties;
import com.visualsearch.exception.SearchDependencyException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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

    private List<ScoredImage> query(Map<String, Object> request, int limit) {
        try {
            QueryResponse response = client.post().uri("/collections/{collection}/points/query", collection)
                    .body(request)
                    .retrieve().body(QueryResponse.class);
            if (response == null || !"ok".equals(response.status()) || response.result() == null
                    || response.result().points() == null || response.result().points().size() > limit
                    || response.result().points().stream().anyMatch(point -> point == null
                    || point.id() == null || point.score() == null || !Double.isFinite(point.score()))) {
                throw new SearchDependencyException("Qdrant returned invalid search results");
            }
            return response.result().points();
        } catch (RestClientException exception) {
            throw new SearchDependencyException("Qdrant search failed", exception);
        }
    }

    // The indexing worker uses the PostgreSQL image UUID as the Qdrant point ID.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoredImage(UUID id, Double score) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record QueryResult(List<ScoredImage> points) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record QueryResponse(String status, QueryResult result) {}
}
