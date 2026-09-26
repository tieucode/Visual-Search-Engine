package com.visualsearch.indexing.processing;

import com.visualsearch.indexing.messaging.ImageIndexingMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ProcessingResult(
        Map<Integer, List<ImageIndexingMessage.ImageItem>> retryGroups,
        int succeeded,
        int failed,
        int skipped) {

    public ProcessingResult {
        Map<Integer, List<ImageIndexingMessage.ImageItem>> copy = new LinkedHashMap<>();
        retryGroups.forEach((attempt, items) -> copy.put(attempt, List.copyOf(items)));
        retryGroups = Map.copyOf(copy);
    }

    public boolean hasRetries() {
        return !retryGroups.isEmpty();
    }
}
