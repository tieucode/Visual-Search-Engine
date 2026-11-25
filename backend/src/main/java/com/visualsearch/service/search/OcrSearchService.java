package com.visualsearch.service.search;

import com.visualsearch.client.ElasticsearchOcrSearchClient;
import com.visualsearch.dto.search.OcrSearchImageData;
import com.visualsearch.dto.search.SearchPageData;
import com.visualsearch.entity.Image;
import com.visualsearch.entity.User;
import com.visualsearch.enums.ImageType;
import com.visualsearch.enums.MediaKind;
import com.visualsearch.enums.SearchType;
import com.visualsearch.exception.BadRequestException;
import com.visualsearch.repository.ImageRepository;
import com.visualsearch.service.ImageUrlResolver;
import com.visualsearch.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OcrSearchService {
    private final ElasticsearchOcrSearchClient elasticsearch;
    private final ImageUrlResolver urls;
    private final SearchHistoryService history;
    private final SearchPaginationService pagination;
    private final ImageRepository images;

    public SearchPageData<OcrSearchImageData> start(String keyword, int size, User user) {
        pagination.validatePageSize(size);
        String query = TextNormalizer.normalize(keyword);
        if (query.isBlank()) throw new BadRequestException("q must not be blank");

        long started = System.nanoTime();
        var hits = searchActive(query, size + 1, null, null);
        int duration = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000);
        UUID searchId = history.createOcrSearch(user, query, duration);
        return response(searchId, size, hits);
    }

    public SearchPageData<OcrSearchImageData> next(UUID searchId, String cursor, User user) {
        String query = history.getOcrQuery(searchId, user);
        SearchPaginationService.OcrCursor position = pagination.decodeOcrCursor(cursor, searchId);
        var hits = searchActive(
                query, position.size() + 1, position.score(), position.imageId());
        return response(searchId, position.size(), hits);
    }

    private SearchPageData<OcrSearchImageData> response(
            UUID searchId, int size, List<ResolvedHit> hits) {
        boolean hasMore = hits.size() > size;
        List<ResolvedHit> page = hits.stream().limit(size).toList();
        List<OcrSearchImageData> items = page.stream().map(ResolvedHit::item).toList();
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            var last = page.getLast();
            nextCursor = pagination.encodeOcrCursor(searchId, last.rankScore(), last.sortImageId(), size);
        }
        return new SearchPageData<>(searchId, SearchType.TEXT_OCR, items, size, hasMore, nextCursor);
    }

    private List<ResolvedHit> searchActive(
            String query, int required, Double afterScore, UUID afterImageId) {
        int fetchSize = Math.max(32, required);
        List<ResolvedHit> active = new ArrayList<>(required);
        Double score = afterScore;
        UUID imageId = afterImageId;
        while (active.size() < required) {
            List<ElasticsearchOcrSearchClient.Hit> batch = elasticsearch.search(query, fetchSize, score, imageId);
            if (batch.isEmpty()) break;
            Map<UUID, Image> available = images.findByIdInAndType(
                            batch.stream().map(ElasticsearchOcrSearchClient.Hit::imageId).toList(), ImageType.UPLOAD)
                    .stream().collect(Collectors.toMap(Image::getId, Function.identity()));
            for (var hit : batch) {
                Image image = available.get(hit.imageId());
                if (image != null && image.getMediaKind() != MediaKind.IMAGE) continue;
                String imageUrl = image == null ? null : urls.imageUrl(image.getPath());
                if (imageUrl == null) continue;
                String thumbnailUrl = urls.thumbnailUrl(image.getThumbnailPath(), imageUrl);
                var item = new OcrSearchImageData(hit.imageId(), imageUrl, thumbnailUrl, image.getWidth(),
                        image.getHeight(), hit.confidenceScore(), hit.rawTexts());
                active.add(new ResolvedHit(item, hit.rankScore(), hit.sortImageId()));
                if (active.size() == required) break;
            }
            var lastScanned = batch.getLast();
            score = lastScanned.rankScore();
            imageId = lastScanned.sortImageId();
            if (batch.size() < fetchSize) break;
        }
        return List.copyOf(active);
    }

    private record ResolvedHit(OcrSearchImageData item, double rankScore, UUID sortImageId) {}
}
