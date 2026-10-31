package com.visualsearch.indexing.processing;
import com.visualsearch.indexing.config.IndexingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class RetryPolicy {
    private final IndexingProperties properties;
    public boolean exhausted(int attempt) { return attempt >= properties.getMaxRetries(); }
}
