package com.visualsearch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class SearchClientConfig {
    @Bean
    public RestClient aiSearchRestClient(SearchProperties properties, RestClient.Builder builder) {
        var ai = properties.getAi();
        return client(builder, ai.getBaseUrl(), ai.getConnectTimeout(), ai.getRequestTimeout());
    }

    @Bean
    public RestClient qdrantSearchRestClient(SearchProperties properties, RestClient.Builder builder) {
        var qdrant = properties.getQdrant();
        return client(builder, qdrant.getBaseUrl(), qdrant.getConnectTimeout(), qdrant.getRequestTimeout());
    }

    private RestClient client(RestClient.Builder builder, String baseUrl, Duration connect, Duration read) {
        // Avoid h2c upgrade: Uvicorn can discard the multipart body when handling an unsupported upgrade.
        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connect)
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(read);
        return builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
