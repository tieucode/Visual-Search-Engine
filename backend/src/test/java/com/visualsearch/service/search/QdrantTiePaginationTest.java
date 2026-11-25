package com.visualsearch.service.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visualsearch.client.QdrantSearchClient;
import com.visualsearch.client.QdrantSearchClient.ScoredImage;
import com.visualsearch.config.SearchProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class QdrantTiePaginationTest {
    @Test
    void readsThroughTheTieBeforeReturningAnyPageFromIt() throws Exception {
        var builder = RestClient.builder().baseUrl("http://qdrant");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new QdrantSearchClient(new SearchProperties(), builder.build());
        var unordered = IntStream.rangeClosed(1, 65).map(i -> 66 - i)
                .mapToObj(id -> new ScoredImage(new UUID(0, id), 0.9)).toList();
        String endpoint = "http://qdrant/collections/images_clips/points/query";
        server.expect(requestTo(endpoint)).andExpect(jsonPath("$.limit").value(64))
                .andExpect(jsonPath("$.offset").value(0)).andExpect(jsonPath("$.params.exact").value(true))
                .andRespond(withSuccess(response(unordered.subList(0, 64)), MediaType.APPLICATION_JSON));
        server.expect(requestTo(endpoint)).andExpect(jsonPath("$.limit").value(128))
                .andExpect(jsonPath("$.offset").value(0))
                .andRespond(withSuccess(response(unordered), MediaType.APPLICATION_JSON));

        assertEquals(List.of(new ScoredImage(new UUID(0, 3), 0.9), new ScoredImage(new UUID(0, 4), 0.9)),
                client.searchClips(List.of(0f, 1f), List.of(), 2, 2));
        server.verify();
    }

    private String response(List<ScoredImage> points) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("status", "ok", "result", Map.of("points", points)));
    }
}
