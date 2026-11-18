package com.visualsearch.indexing.client.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.visualsearch.indexing.config.IndexingProperties;
import com.visualsearch.indexing.persistence.OcrIndexDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class ElasticsearchOcrIndexClient {
    private final RestClient indexingElasticsearchRestClient;
    private final IndexingProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean initialized = new AtomicBoolean();

    public void upsert(OcrIndexDocument document, long revision) {
        ensureIndex();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("imageId", document.imageId().toString());
        body.put("imagePath", document.imagePath());
        if (document.thumbnailPath() == null) body.putNull("thumbnailPath");
        else body.put("thumbnailPath", document.thumbnailPath());
        if (document.width() == null) body.putNull("width"); else body.put("width", document.width());
        if (document.height() == null) body.putNull("height"); else body.put("height", document.height());
        body.set("normalizedTexts", objectMapper.valueToTree(document.normalizedTexts()));
        body.set("rawTexts", objectMapper.valueToTree(document.rawTexts()));
        if (document.confidenceScore() == null) body.putNull("confidenceScore");
        else body.put("confidenceScore", document.confidenceScore());
        write(document.imageId(), revision, body);
    }

    public void markDeleted(UUID imageId, long revision) {
        ensureIndex();
        ObjectNode tombstone = objectMapper.createObjectNode();
        tombstone.put("imageId", imageId.toString());
        tombstone.put("deleted", true);
        write(imageId, revision, tombstone);
    }

    private void write(UUID imageId, long revision, ObjectNode body) {
        try {
            indexingElasticsearchRestClient.put()
                    .uri("/{index}/_doc/{id}?version={revision}&version_type=external",
                            properties.getElasticsearch().getOcrIndex(), imageId, revision)
                    .body(body).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.Conflict conflict) {
            // A duplicate or superseded write has already reached this document.
            var response = conflict.getResponseBodyAs(JsonNode.class);
            if (response == null || !"version_conflict_engine_exception".equals(response.at("/error/type").asText())) {
                throw conflict;
            }
        }
    }

    private void ensureIndex() {
        if (initialized.get()) return;
        String index = properties.getElasticsearch().getOcrIndex();
        try {
            indexingElasticsearchRestClient.get().uri("/{index}", index).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound missing) {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode props = body.putObject("mappings").putObject("properties");
            props.putObject("imageId").put("type", "keyword");
            props.putObject("imagePath").put("type", "keyword");
            props.putObject("thumbnailPath").put("type", "keyword");
            props.putObject("deleted").put("type", "boolean");
            props.putObject("width").put("type", "integer");
            props.putObject("height").put("type", "integer");
            props.putObject("normalizedTexts").put("type", "text").put("analyzer", "standard");
            props.putObject("rawTexts").put("type", "text").put("index", false);
            props.putObject("confidenceScore").put("type", "double");
            indexingElasticsearchRestClient.put().uri("/{index}", index).body(body).retrieve().toBodilessEntity();
        }
        initialized.set(true);
    }
}
