package com.visualsearch.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.visualsearch.config.SearchProperties;
import com.visualsearch.exception.SearchDependencyException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ElasticsearchOcrSearchClient {
    private final RestClient elasticsearchSearchRestClient;
    private final SearchProperties properties;
    private final ObjectMapper objectMapper;

    public List<Hit> search(String query, int limit, Double afterScore, UUID afterImageId) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", limit);
        body.put("track_total_hits", false);
        ObjectNode bool = body.putObject("query").putObject("bool");
        ArrayNode must = bool.putArray("must");
        must.addObject().putObject("match").putObject("normalizedTexts")
                .put("query", query)
                .put("operator", "and")
                .put("fuzziness", "AUTO")
                .put("prefix_length", 1)
                .put("max_expansions", 50);
        ArrayNode sort = body.putArray("sort");
        sort.addObject().putObject("_score").put("order", "desc");
        sort.addObject().putObject("imageId").put("order", "asc");
        if (afterScore != null && afterImageId != null) {
            body.putArray("search_after").add(afterScore).add(afterImageId.toString());
        }

        try {
            JsonNode response = elasticsearchSearchRestClient.post()
                    .uri("/{index}/_search", properties.getElasticsearch().getOcrIndex())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode hits = response == null ? null : response.path("hits").path("hits");
            if (hits == null || !hits.isArray()) {
                throw new SearchDependencyException("Elasticsearch returned an invalid OCR search response");
            }
            List<Hit> result = new ArrayList<>();
            for (JsonNode hit : hits) {
                JsonNode source = hit.path("_source");
                JsonNode sortValues = hit.path("sort");
                UUID imageId = UUID.fromString(source.path("imageId").asText());
                double rankScore = sortValues.path(0).asDouble();
                UUID sortImageId = UUID.fromString(sortValues.path(1).asText());
                List<String> rawTexts = new ArrayList<>();
                JsonNode raw = source.path("rawTexts");
                if (raw.isArray()) raw.forEach(value -> rawTexts.add(value.asText()));
                result.add(new Hit(
                        imageId,
                        source.path("imagePath").asText(),
                        nullableText(source.path("thumbnailPath")),
                        nullableInt(source.path("width")),
                        nullableInt(source.path("height")),
                        source.path("confidenceScore").isNumber()
                                ? source.path("confidenceScore").asDouble() : 0.0,
                        List.copyOf(rawTexts),
                        rankScore,
                        sortImageId));
            }
            return List.copyOf(result);
        } catch (SearchDependencyException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SearchDependencyException("Elasticsearch OCR search failed", exception);
        }
    }

    private String nullableText(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Integer nullableInt(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    public record Hit(UUID imageId, String imagePath, String thumbnailPath, Integer width, Integer height,
                      double confidenceScore, List<String> rawTexts, double rankScore, UUID sortImageId) {}
}
