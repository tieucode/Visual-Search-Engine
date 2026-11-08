package com.visualsearch.indexing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ElasticsearchClientConfiguration {
    @Bean
    RestClient indexingElasticsearchRestClient(IndexingProperties properties) {
        var config = properties.getElasticsearch();
        return DependencyHttpClients.create(config.getBaseUrl(), config.getConnectTimeout(),
                config.getRequestTimeout());
    }
}
