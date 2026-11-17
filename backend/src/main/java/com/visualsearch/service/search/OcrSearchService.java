package com.visualsearch.service.search;

import com.visualsearch.client.ElasticsearchOcrSearchClient;
import com.visualsearch.dto.search.OcrSearchImageData;
import com.visualsearch.dto.search.SearchPageData;
import com.visualsearch.entity.User;
import com.visualsearch.enums.ImageType;
import com.visualsearch.enums.SearchType;
import com.visualsearch.exception.BadRequestException;
import com.visualsearch.repository.ImageRepository;
import com.visualsearch.service.MinioStorageService;
import com.visualsearch.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OcrSearchService {
    private final ElasticsearchOcrSearchClient elasticsearch;
    private final MinioStorageService storage;
    private final SearchHistoryService history;
    private final SearchPaginationService pagination;
    private final ImageRepository images;

    public SearchPageData<OcrSearchImageData> start(String keyword, int size, User user) {
        pagination.validatePageSize(size);
        String query = TextNormalizer.normalize(keyword);
        if (query.isBlank()) throw new BadRequestException("q must not be blank");

        long started = System.nanoTime();
        List<ElasticsearchOcrSearchClient.Hit> hits = searchActive(query, size + 1, null, null);
        int duration = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000);
        UUID searchId = history.createOcrSearch(user, query, duration);
        return response(searchId, size, hits);
    }

    public SearchPageData<OcrSearchImageData> next(UUID searchId, String cursor, User user) {
        String query = history.getOcrQuery(searchId, user);
        SearchPaginationService.OcrCursor position = pagination.decodeOcrCursor(cursor, searchId);
        List<ElasticsearchOcrSearchClient.Hit> hits = searchActive(
                query, position.size() + 1, position.score(), position.imageId());
        return response(searchId, position.size(), hits);
    }

    private SearchPageData<OcrSearchImageData> response(
            UUID searchId, int size, List<ElasticsearchOcrSearchClient.Hit> hits) {
        boolean hasMore = hits.size() > size;
        List<ElasticsearchOcrSearchClient.Hit> page = hits.stream().limit(size).toList();
        List<OcrSearchImageData> items = page.stream().map(this::toSearchImage).toList();
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            var last = page.getLast();
            nextCursor = pagination.encodeOcrCursor(searchId, last.rankScore(), last.sortImageId(), size);
        }
        return new SearchPageData<>(searchId, SearchType.TEXT_OCR, items, size, hasMore, nextCursor);
    }

    private List<ElasticsearchOcrSearchClient.Hit> searchActive(
            String query, int required, Double afterScore, UUID afterImageId) {
        int fetchSize = Math.max(32, required);
        List<ElasticsearchOcrSearchClient.Hit> active = new java.util.ArrayList<>(required);
        Double score = afterScore;
        UUID imageId = afterImageId;
        while (active.size() < required) {
            List<ElasticsearchOcrSearchClient.Hit> batch = elasticsearch.search(query, fetchSize, score, imageId);
            if (batch.isEmpty()) break;
            Set<UUID> existing = images.findByIdInAndType(
                            batch.stream().map(ElasticsearchOcrSearchClient.Hit::imageId).toList(), ImageType.UPLOAD)
                    .stream().map(image -> image.getId()).collect(Collectors.toSet());
            for (var hit : batch) {
                if (existing.contains(hit.imageId())) active.add(hit);
                if (active.size() == required) break;
            }
            var lastScanned = batch.getLast();
            score = lastScanned.rankScore();
            imageId = lastScanned.sortImageId();
            if (batch.size() < fetchSize) break;
        }
        return List.copyOf(active);
    }

    private OcrSearchImageData toSearchImage(ElasticsearchOcrSearchClient.Hit hit) {
        String imageUrl = storage.getPublicImageUrl(hit.imagePath());
        String thumbnailUrl = hit.thumbnailPath() == null || hit.thumbnailPath().isBlank()
                ? imageUrl : storage.getPresignedThumbnailUrl(hit.thumbnailPath());
        return new OcrSearchImageData(hit.imageId(), imageUrl, thumbnailUrl, hit.width(), hit.height(),
                hit.confidenceScore(), hit.rawTexts());
    }
}
