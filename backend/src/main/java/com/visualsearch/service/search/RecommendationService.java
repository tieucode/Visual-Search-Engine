package com.visualsearch.service.search;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.visualsearch.client.QdrantSearchClient;
import com.visualsearch.client.QdrantSearchClient.ScoredImage;
import com.visualsearch.config.SearchProperties;
import com.visualsearch.dto.search.SearchImageData;
import com.visualsearch.dto.search.SearchPageData;
import com.visualsearch.entity.Image;
import com.visualsearch.entity.SearchClick;
import com.visualsearch.entity.User;
import com.visualsearch.enums.ImageType;
import com.visualsearch.enums.MediaKind;
import com.visualsearch.enums.SearchType;
import com.visualsearch.exception.ResourceNotFoundException;
import com.visualsearch.exception.SearchDependencyException;
import com.visualsearch.repository.ImageRepository;
import com.visualsearch.repository.RecommendationRepository;
import com.visualsearch.repository.SearchClickRepository;
import com.visualsearch.service.ImageUrlResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {
    private static final int FEED_LIMIT = 100;
    private final SearchClickRepository clicks;
    private final RecommendationRepository recommendations;
    private final ImageRepository images;
    private final QdrantSearchClient qdrant;
    private final ImageUrlResolver urls;
    private final SearchPaginationService pagination;
    private final SearchProperties properties;

    private final Cache<UUID, Feed> feeds = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    @Transactional
    public void recordClick(User user, UUID imageId) {
        Image image = images.findById(imageId).filter(item -> item.getType() == ImageType.UPLOAD)
                .orElseThrow(() -> new ResourceNotFoundException("Library image not found"));
        clicks.save(SearchClick.builder().user(user).clickedImage(image).build());
    }

    public SearchPageData<SearchImageData> start(User user, int size) {
        pagination.validatePageSize(size);
        List<UUID> recent = clicks.findRecentImageIds(user.getId());
        List<ScoredImage> ranked = new ArrayList<>(personalize(recent));
        int missing = FEED_LIMIT - ranked.size();
        if (missing > 0) {
            var excluded = new HashSet<>(recent);
            ranked.forEach(hit -> excluded.add(hit.id()));
            recommendations.findPopularOrRecent(excluded, missing)
                    .forEach(id -> ranked.add(new ScoredImage(id, 0.0)));
        }

        UUID feedId = UUID.randomUUID();
        Feed feed = new Feed(user.getId(), List.copyOf(ranked));
        SearchPageData<SearchImageData> first = page(feedId, feed, 0, size);
        if (first.hasMore()) {
            feeds.put(feedId, feed);
        }
        return first;
    }

    public SearchPageData<SearchImageData> next(User user, UUID feedId, String cursor) {
        Feed feed = feeds.getIfPresent(feedId);
        if (feed == null || !feed.userId().equals(user.getId())) {
            throw new ResourceNotFoundException("Recommendation feed not found");
        }
        var position = pagination.decodeCursor(cursor, feedId);
        return page(feedId, feed, position.offset(), position.size());
    }

    private List<ScoredImage> personalize(List<UUID> recent) {
        if (recent.isEmpty()) return List.of();

        List<RecommendationRanking.Interest> interests;
        List<List<ScoredImage>> matches;
        try {
            var media = loadImages(recent);
            List<UUID> stillIds = recent.stream().filter(id -> media.containsKey(id)
                    && media.get(id).getMediaKind() == MediaKind.IMAGE).toList();
            List<UUID> clipIds = recent.stream().filter(id -> media.containsKey(id)
                    && media.get(id).getMediaKind() != MediaKind.IMAGE).toList();
            var vectors = new HashMap<UUID, List<Float>>();
            if (!stillIds.isEmpty()) vectors.putAll(qdrant.getVectors(stillIds));
            qdrant.getClipFrames(clipIds).forEach((id, frames) -> vectors.put(id, frames.getFirst().vector()));
            interests = RecommendationRanking.cluster(recent, vectors,
                    properties.getQdrant().getVectorSize(),
                    properties.getRecommendation().getClusterThreshold());
            if (interests.isEmpty()) return List.of();

            matches = qdrant.searchBatch(interests.stream().map(RecommendationRanking.Interest::center)
                    .toList(), recent, FEED_LIMIT);
        } catch (SearchDependencyException exception) {
            log.warn("Qdrant recommendations unavailable, using popular or recent images: {}", exception.getMessage());
            return List.of();
        }
        List<UUID> ids = matches.stream().flatMap(List::stream).map(ScoredImage::id).distinct().toList();
        return RecommendationRanking.interleave(interests, matches, loadImages(ids).keySet(), recent, FEED_LIMIT);
    }

    private SearchPageData<SearchImageData> page(UUID feedId, Feed feed, long offset, int size) {
        List<ScoredImage> ranked = feed.ranked();
        int start = (int) Math.min(offset, ranked.size());
        var available = loadImages(ranked.subList(start, ranked.size()).stream().map(ScoredImage::id).toList());
        List<SearchImageData> items = new ArrayList<>(size);
        Long nextOffset = null;
        for (int index = start; index < ranked.size(); index++) {
            ScoredImage hit = ranked.get(index);
            Image image = available.get(hit.id());
            if (image == null) continue;
            String imageUrl = urls.imageUrl(image.getPath());
            if (imageUrl == null) continue;
            if (items.size() == size) {
                nextOffset = (long) index;
                break;
            }
            items.add(new SearchImageData(image.getId(), imageUrl,
                    urls.thumbnailUrl(image.getThumbnailPath(), imageUrl),
                    image.getWidth(), image.getHeight(), hit.score(), image.getMediaKind(), image.getDurationMs(), null));
        }

        return pagination.createPage(feedId, SearchType.RECOMMENDATION, size,
                new SimilaritySearchService.ResultPage(List.copyOf(items), nextOffset, null));
    }

    private Map<UUID, Image> loadImages(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return images.findSearchableMedia(ids).stream()
                .collect(Collectors.toMap(Image::getId, Function.identity()));
    }

    private record Feed(UUID userId, List<ScoredImage> ranked) {}
}
