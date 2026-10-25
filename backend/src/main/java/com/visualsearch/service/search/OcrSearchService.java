package com.visualsearch.service.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visualsearch.dto.search.OcrSearchImageData;
import com.visualsearch.dto.search.SearchPageData;
import com.visualsearch.entity.User;
import com.visualsearch.enums.SearchType;
import com.visualsearch.exception.BadRequestException;
import com.visualsearch.repository.ImageOcrRepository;
import com.visualsearch.repository.projection.OcrSearchProjection;
import com.visualsearch.service.MinioStorageService;
import com.visualsearch.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OcrSearchService {
    private final ImageOcrRepository imageOcrRepository;
    private final MinioStorageService storage;
    private final SearchHistoryService history;
    private final SearchPaginationService pagination;
    private final ObjectMapper objectMapper;

    @Transactional
    public SearchPageData<OcrSearchImageData> start(String keyword, int size, User user) {
        pagination.validatePageSize(size);
        String query = TextNormalizer.normalize(keyword);
        if (query.isBlank()) throw new BadRequestException("q must not be blank");

        long started = System.nanoTime();
        List<OcrSearchProjection> rows = imageOcrRepository.searchByNormalizedTextPage(query, size + 1, 0);
        int duration = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000);
        UUID searchId = history.createOcrSearch(user, query, duration);
        return response(searchId, size, 0, rows);
    }

    @Transactional(readOnly = true)
    public SearchPageData<OcrSearchImageData> next(UUID searchId, String cursor, User user) {
        // Verify ownership before running the OCR query.
        String query = history.getOcrQuery(searchId, user);
        SearchPaginationService.Cursor position = pagination.decodeCursor(cursor, searchId);
        List<OcrSearchProjection> rows = imageOcrRepository.searchByNormalizedTextPage(
                query, position.size() + 1, position.offset());
        return response(searchId, position.size(), position.offset(), rows);
    }

    private SearchPageData<OcrSearchImageData> response(UUID searchId, int size, long offset,
                                                        List<OcrSearchProjection> rows) {
        boolean hasMore = rows.size() > size;
        List<OcrSearchImageData> items = rows.stream().limit(size).map(this::toSearchImage).toList();
        String nextCursor = hasMore ? pagination.encodeCursor(searchId, offset + size, size) : null;
        return new SearchPageData<>(searchId, SearchType.TEXT_OCR, items, size, hasMore, nextCursor);
    }

    private OcrSearchImageData toSearchImage(OcrSearchProjection row) {
        String imageUrl = storage.getPublicImageUrl(row.getImagePath());
        String thumbnailUrl = row.getThumbnailPath() == null || row.getThumbnailPath().isBlank()
                ? imageUrl : storage.getPresignedThumbnailUrl(row.getThumbnailPath());
        try {
            List<String> rawTexts = objectMapper.readValue(row.getRawTexts(), new TypeReference<>() {});
            return new OcrSearchImageData(row.getImageId(), imageUrl, thumbnailUrl, row.getWidth(), row.getHeight(),
                    row.getConfidenceScore() == null ? 0.0 : row.getConfidenceScore(), rawTexts);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not parse OCR texts returned by database", exception);
        }
    }
}
