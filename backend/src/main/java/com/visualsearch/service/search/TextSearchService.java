package com.visualsearch.service.search;

import com.visualsearch.client.AiEmbeddingClient;
import com.visualsearch.dto.search.SearchPageData;
import com.visualsearch.dto.search.SearchImageData;
import com.visualsearch.dto.search.TextSearchRequest;
import com.visualsearch.entity.User;
import com.visualsearch.enums.SearchType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TextSearchService {
    private final AiEmbeddingClient embeddings;
    private final SimilaritySearchService similarity;
    private final SearchHistoryService history;
    private final SearchPaginationService pagination;

    public SearchPageData<SearchImageData> start(TextSearchRequest request, int size, User user) {
        pagination.validatePageSize(size);
        long started = System.nanoTime();
        var vector = embeddings.embedText(request.text());
        var results = similarity.search(vector, 0, size);
        int duration = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000);
        UUID searchId = history.createTextSearch(user, request.text(), duration);
        return pagination.createPage(searchId, SearchType.TEXT_SEMANTIC, size, results);
    }

    public SearchPageData<SearchImageData> next(UUID searchId, String cursor, User user) {
        // Check ownership and search type before calling AI or Qdrant.
        String text = history.getQueryText(searchId, user);
        var position = pagination.decodeCursor(cursor, searchId);
        var vector = embeddings.embedText(text);
        var results = similarity.search(vector, position.offset(), position.size());
        return pagination.createPage(searchId, SearchType.TEXT_SEMANTIC, position.size(), results);
    }
}
