package com.visualsearch.service.search;

import com.visualsearch.client.QdrantSearchClient;
import com.visualsearch.dto.search.SearchImageData;
import com.visualsearch.entity.Image;
import com.visualsearch.enums.ImageType;
import com.visualsearch.repository.ImageRepository;
import com.visualsearch.service.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Vector retrieval shared by image search and, later, semantic text search. */
@Service
@RequiredArgsConstructor
public class SimilaritySearchService {
    private final QdrantSearchClient qdrant;
    private final ImageRepository images;
    private final MinioStorageService storage;

    public ResultPage search(List<Float> vector, long offset, int size) {
        List<SearchImageData> items = new ArrayList<>();
        long scanOffset = offset;
        int limit = size + 1;
        while (true) {
            var points = qdrant.search(vector, scanOffset, limit);
            if (points.isEmpty()) {
                return new ResultPage(List.copyOf(items), null);
            }
            Map<UUID, Image> available = images.findByIdInAndType(
                            points.stream().map(QdrantSearchClient.ScoredImage::id).toList(), ImageType.UPLOAD)
                    .stream().collect(Collectors.toMap(Image::getId, Function.identity()));
            for (var point : points) {
                Image image = available.get(point.id());
                if (image != null) {
                    // Peek at one *visible* result; leave it for the next request.
                    if (items.size() == size) {
                        return new ResultPage(List.copyOf(items), scanOffset);
                    }
                    String imageUrl = storage.getPublicImageUrl(image.getPath());
                    String thumbnailUrl = image.getThumbnailPath() == null ? imageUrl
                            : storage.getPresignedThumbnailUrl(image.getThumbnailPath());
                    items.add(new SearchImageData(image.getId(), imageUrl, thumbnailUrl,
                            image.getWidth(), image.getHeight(), point.score()));
                }
                // Advance by Qdrant candidates, including deleted/missing images, not by DTO count.
                scanOffset++;
            }
            if (points.size() < limit) {
                return new ResultPage(List.copyOf(items), null);
            }
        }
    }

    public record ResultPage(List<SearchImageData> items, Long nextOffset) {}
}
