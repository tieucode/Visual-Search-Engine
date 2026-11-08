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

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class ElasticsearchOcrIndexClient {
    private final RestClient indexingElasticsearchRestClient;
    private final IndexingProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean initialized = new AtomicBoolean();

    public void upsert(OcrIndexDocument document) {
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
        indexingElasticsearchRestClient.put()
                .uri("/{index}/_doc/{id}", properties.getElasticsearch().getOcrIndex(), document.imageId())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public void initializeIndex() {
        ensureIndex();
    }

    public void delete(String imageId) {
        ensureIndex();
        try {
            indexingElasticsearchRestClient.delete()
                    .uri("/{index}/_doc/{id}", properties.getElasticsearch().getOcrIndex(), imageId)
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
            // Deletion is idempotent; the document may already be absent.
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
