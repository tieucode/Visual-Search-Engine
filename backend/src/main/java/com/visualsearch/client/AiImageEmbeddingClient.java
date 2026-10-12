package com.visualsearch.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.visualsearch.config.SearchProperties;
import com.visualsearch.exception.BadRequestException;
import com.visualsearch.exception.SearchDependencyException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
public class AiImageEmbeddingClient {
    private final RestClient client;
    private final int vectorSize;

    public AiImageEmbeddingClient(SearchProperties properties,
                                  @Qualifier("aiSearchRestClient") RestClient client) {
        this.client = client;
        vectorSize = properties.getQdrant().getVectorSize();
    }

    public List<Float> embed(byte[] bytes, String filename, String contentType) {
        var resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new HttpEntity<>(resource, headers));
        try {
            EmbeddingResponse response = client.post().uri("/api/embedding/image")
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(body)
                    .retrieve().body(EmbeddingResponse.class);
            if (response == null || !Boolean.TRUE.equals(response.success())
                    || response.dimension() == null || response.dimension() != vectorSize
                    || response.embedding() == null || response.embedding().size() != vectorSize
                    || response.embedding().stream().anyMatch(value -> value == null || !Float.isFinite(value))
                    || response.embedding().stream().allMatch(value -> value == 0f)) {
                throw new SearchDependencyException("AI service returned an invalid image embedding");
            }
            return response.embedding();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 400 || exception.getStatusCode().value() == 422) {
                throw new BadRequestException("AI service could not process the query image");
            }
            throw new SearchDependencyException("AI service is unavailable", exception);
        } catch (RestClientException exception) {
            throw new SearchDependencyException("AI service request failed", exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingResponse(Boolean success, Integer dimension, List<Float> embedding) {}
}
