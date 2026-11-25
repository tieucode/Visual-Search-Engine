package com.visualsearch.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.visualsearch.config.SearchProperties;
import com.visualsearch.exception.SearchDependencyException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class QdrantSearchClient {
    public static final Comparator<ScoredImage> RANKING = Comparator
            .comparingDouble((ScoredImage point) -> point.score()).reversed()
            .thenComparing(point -> point.id().toString());

    private final RestClient client;
    private final String collection;
    private final String clips;
    private final int vectorSize;

    public QdrantSearchClient(SearchProperties properties,
                               @Qualifier("qdrantSearchRestClient") RestClient client) {
        this.client = client;
        collection = properties.getQdrant().getCollection();
        clips = collection + "_clips";
        vectorSize = properties.getQdrant().getVectorSize();
    }

    public List<ScoredImage> searchSimilar(UUID imageId, long offset, int limit) {
        return query(collection, request(imageId, List.of(imageId), offset, limit), limit);
    }

    public List<ScoredImage> searchLegacy(List<Float> vector, long offset, int limit) {
        return query(collection, request(vector, List.of(), offset, limit), limit);
    }

    public List<ScoredImage> searchImages(List<Float> vector, List<UUID> excluded, long offset, int limit) {
        return searchRanked(collection, vector, excluded, offset, limit);
    }

    public List<ScoredImage> searchClips(List<Float> vector, List<UUID> excluded, long offset, int limit) {
        // MaxSim with a one-row query gives the score of the best matching clip frame.
        return searchRanked(clips, List.of(vector), excluded, offset, limit);
    }

    private List<ScoredImage> searchRanked(String target, Object vector, List<UUID> excluded, long offset, int limit) {
        int start = Math.toIntExact(offset);
        int end = Math.addExact(start, limit);
        int window = Math.max(64, Math.addExact(end, 1));
        while (true) {
            var body = request(vector, excluded, 0, window);
            body.put("params", Map.of("exact", true));
            var matches = query(target, body, window);
            if (matches.size() <= start) return List.of();

            // Qdrant can change the order of equal-score points as the query limit changes.
            // Read through the entire score group at the page boundary before applying the UUID order.
            if (matches.size() < window
                    || !matches.get(end - 1).score().equals(matches.getLast().score())) {
                return matches.stream().sorted(RANKING).skip(start).limit(limit).toList();
            }
            window = Math.multiplyExact(window, 2);
        }
    }

    private Map<String, Object> request(Object vector, List<UUID> excluded, long offset, int limit) {
        Map<String, Object> body = new HashMap<>(Map.of("query", vector, "offset", offset,
                "limit", limit, "with_payload", false, "with_vector", false));
        if (!excluded.isEmpty()) {
            body.put("filter", Map.of("must_not", List.of(Map.of("has_id", excluded))));
        }
        return body;
    }

    public Map<UUID, List<ClipFrame>> getClipFrames(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        try {
            ClipVectorResponse response = client.post().uri("/collections/{collection}/points", clips)
                    .body(Map.of("ids", ids, "with_payload", true, "with_vector", true))
                    .retrieve().body(ClipVectorResponse.class);
            if (response == null || !"ok".equals(response.status()) || response.result() == null) {
                throw new SearchDependencyException("Qdrant returned invalid clip vectors");
            }
            Map<UUID, List<ClipFrame>> frames = new HashMap<>();
            for (ClipPoint point : response.result()) {
                if (point == null || point.id() == null || point.vector() == null || point.payload() == null
                        || point.payload().frameTimesMs() == null || point.vector().isEmpty()
                        || point.vector().size() > 8 || point.vector().size() != point.payload().frameTimesMs().size()) {
                    throw new SearchDependencyException("Qdrant returned invalid clip frame metadata");
                }
                List<ClipFrame> values = new ArrayList<>();
                int previous = -1;
                for (int index = 0; index < point.vector().size(); index++) {
                    Integer time = point.payload().frameTimesMs().get(index);
                    List<Float> vector = point.vector().get(index);
                    if (time == null || time <= previous || time >= 30_000 || vector == null
                            || vector.size() != vectorSize
                            || vector.stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
                        throw new SearchDependencyException("Qdrant returned an invalid clip frame");
                    }
                    values.add(new ClipFrame(time, vector));
                    previous = time;
                }
                frames.put(point.id(), List.copyOf(values));
            }
            return frames;
        } catch (HttpClientErrorException.NotFound exception) {
            return Map.of();
        } catch (RestClientException exception) {
            throw new SearchDependencyException("Qdrant clip vector retrieval failed", exception);
        }
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
        var imageMatches = searchBatch(collection, vectors.stream()
                .map(vector -> request(vector, excluded, 0, limit)).toList(), limit);
        var clipMatches = searchBatch(clips, vectors.stream()
                .map(vector -> request(List.of(vector), excluded, 0, limit)).toList(), limit);
        List<List<ScoredImage>> merged = new ArrayList<>();
        for (int index = 0; index < vectors.size(); index++) {
            var matches = new ArrayList<>(imageMatches.get(index));
            matches.addAll(clipMatches.get(index));
            matches.sort(RANKING);
            merged.add(matches);
        }
        return merged;
    }

    private List<List<ScoredImage>> searchBatch(String target, List<Map<String, Object>> queries, int limit) {
        try {
            BatchQueryResponse response = client.post()
                    .uri("/collections/{collection}/points/query/batch", target)
                    .body(Map.of("searches", queries))
                    .retrieve().body(BatchQueryResponse.class);
            if (response == null || !"ok".equals(response.status()) || response.result() == null
                    || response.result().size() != queries.size()) {
                throw new SearchDependencyException("Qdrant returned invalid batch search results");
            }
            return response.result().stream().map(result -> checkedPoints(result, limit)).toList();
        } catch (HttpClientErrorException.NotFound exception) {
            if (target.equals(clips)) return Collections.nCopies(queries.size(), List.of());
            throw new SearchDependencyException("Qdrant image collection is unavailable", exception);
        } catch (RestClientException exception) {
            throw new SearchDependencyException("Qdrant batch search failed", exception);
        }
    }

    private List<ScoredImage> query(String target, Map<String, Object> request, int limit) {
        try {
            QueryResponse response = client.post().uri("/collections/{collection}/points/query", target)
                    .body(request)
                    .retrieve().body(QueryResponse.class);
            if (response == null || !"ok".equals(response.status())) {
                throw new SearchDependencyException("Qdrant returned invalid search results");
            }
            return checkedPoints(response.result(), limit);
        } catch (HttpClientErrorException.NotFound exception) {
            if (target.equals(clips)) return List.of();
            throw new SearchDependencyException("Qdrant image query point or collection is unavailable", exception);
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
    public record ClipFrame(int timestampMs, List<Float> vector) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClipPayload(List<Integer> frameTimesMs) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClipPoint(UUID id, List<List<Float>> vector, ClipPayload payload) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClipVectorResponse(String status, List<ClipPoint> result) {}
}
