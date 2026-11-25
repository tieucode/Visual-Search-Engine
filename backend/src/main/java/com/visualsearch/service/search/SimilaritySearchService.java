package com.visualsearch.service.search;

import com.visualsearch.client.QdrantSearchClient;
import com.visualsearch.client.QdrantSearchClient.ClipFrame;
import com.visualsearch.client.QdrantSearchClient.ScoredImage;
import com.visualsearch.dto.search.SearchImageData;
import com.visualsearch.entity.Image;
import com.visualsearch.enums.MediaKind;
import com.visualsearch.exception.ResourceNotFoundException;
import com.visualsearch.exception.SearchDependencyException;
import com.visualsearch.repository.ImageRepository;
import com.visualsearch.service.ImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/** Vector retrieval shared by image search and semantic text search. */
@Service
@RequiredArgsConstructor
public class SimilaritySearchService {
    private final QdrantSearchClient qdrant;
    private final ImageRepository images;
    private final ImageUrlResolver urls;

    public ResultPage search(List<Float> vector, long offset, Long clipOffset, int size) {
        if (clipOffset == null) {
            return merge(null, new Candidates((from, limit) -> qdrant.searchLegacy(vector, from, limit), offset, size),
                    null, size);
        }
        return search(vector, List.of(), offset, clipOffset, size);
    }

    public ResultPage searchSimilar(Image source, Integer frameMs, long offset, Long clipOffset, int size) {
        UUID id = source.getId();
        if (clipOffset == null && source.getMediaKind() == MediaKind.IMAGE) {
            return merge(null, new Candidates((from, limit) -> qdrant.searchSimilar(id, from, limit), offset, size),
                    null, size);
        }
        List<Float> vector;
        if (source.getMediaKind() == MediaKind.IMAGE) {
            vector = qdrant.getVectors(List.of(id)).get(id);
            if (vector == null) throw new ResourceNotFoundException("Library image has no embedding");
        } else {
            if (images.findSearchableMedia(List.of(id)).isEmpty()) {
                throw new ResourceNotFoundException("Clip indexing is incomplete");
            }
            var frames = qdrant.getClipFrames(List.of(id)).getOrDefault(id, List.of());
            vector = frames.stream().filter(frame -> frameMs == null || frame.timestampMs() == frameMs)
                    .findFirst().orElseThrow(() -> new ResourceNotFoundException("Clip frame not found")).vector();
        }
        return search(vector, List.of(id), offset, clipOffset, size);
    }

    private ResultPage search(List<Float> vector, List<UUID> excluded, long offset, Long clipOffset, int size) {
        var stills = new Candidates((from, limit) -> qdrant.searchImages(vector, excluded, from, limit), offset, size);
        var clips = clipOffset == null ? null
                : new Candidates((from, limit) -> qdrant.searchClips(vector, excluded, from, limit), clipOffset, size);
        return merge(vector, stills, clips, size);
    }

    private ResultPage merge(List<Float> vector, Candidates stills, Candidates clips, int size) {
        List<Candidate> selected = new ArrayList<>();
        boolean hasMore = false;
        while (true) {
            Candidate still = stills.peek();
            Candidate clip = clips == null ? null : clips.peek();
            if (still == null && clip == null) break;
            if (selected.size() == size) {
                hasMore = true;
                break;
            }
            Candidates best = clip == null || still != null && QdrantSearchClient.RANKING.compare(
                    still.point(), clip.point()) <= 0 ? stills : clips;
            selected.add(best.take());
        }

        List<UUID> clipIds = selected.stream().map(Candidate::image)
                .filter(image -> image.getMediaKind() != MediaKind.IMAGE).map(Image::getId).toList();
        var frames = clipIds.isEmpty() ? Map.<UUID, List<ClipFrame>>of() : qdrant.getClipFrames(clipIds);
        var items = selected.stream().map(candidate -> {
            Image image = candidate.image();
            Integer time = image.getMediaKind() == MediaKind.IMAGE ? null
                    : bestFrame(vector, frames.getOrDefault(image.getId(), List.of()));
            String thumbnail = time == null ? image.getThumbnailPath() : frameThumbnail(image.getId(), time);
            return new SearchImageData(image.getId(), candidate.url(), urls.thumbnailUrl(thumbnail, candidate.url()),
                    image.getWidth(), image.getHeight(), candidate.point().score(),
                    image.getMediaKind(), image.getDurationMs(), time);
        }).toList();
        return new ResultPage(items, hasMore ? stills.offset : null,
                hasMore && clips != null ? clips.offset : null);
    }

    static String frameThumbnail(UUID id, int time) {
        return "clips/" + id + "/" + time + ".jpg";
    }

    private int bestFrame(List<Float> vector, List<ClipFrame> frames) {
        return frames.stream().max(Comparator.comparingDouble((ClipFrame frame) -> cosine(vector, frame.vector()))
                .thenComparing(Comparator.comparingInt(ClipFrame::timestampMs).reversed()))
                .orElseThrow(() -> new SearchDependencyException("Clip frame vectors are unavailable")).timestampMs();
    }

    private static double cosine(List<Float> left, List<Float> right) {
        double product = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.size(); index++) {
            double a = left.get(index);
            double b = right.get(index);
            product += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        return product / Math.sqrt(leftNorm * rightNorm);
    }

    private record Candidate(ScoredImage point, Image image, String url) {}

    /** The cursor tracks consumed Qdrant candidates, including invisible or deleted items. */
    private final class Candidates {
        private final BiFunction<Long, Integer, List<ScoredImage>> retrieve;
        private final int limit;
        private long offset;
        private int index;
        private boolean last;
        private List<ScoredImage> points = List.of();
        private Map<UUID, Image> available = Map.of();
        private Candidate next;

        Candidates(BiFunction<Long, Integer, List<ScoredImage>> retrieve, long offset, int size) {
            this.retrieve = retrieve;
            this.offset = offset;
            this.limit = size + 1;
        }

        Candidate peek() {
            while (next == null) {
                if (index == points.size()) {
                    if (last) return null;
                    points = retrieve.apply(offset, limit);
                    index = 0;
                    last = points.size() < limit;
                    if (points.isEmpty()) return null;
                    available = images.findSearchableMedia(points.stream().map(ScoredImage::id).toList())
                            .stream().collect(Collectors.toMap(Image::getId, Function.identity()));
                }
                ScoredImage point = points.get(index);
                Image image = available.get(point.id());
                String url = image == null ? null : urls.imageUrl(image.getPath());
                if (url == null) advance();
                else next = new Candidate(point, image, url);
            }
            return next;
        }

        Candidate take() {
            var result = next;
            advance();
            return result;
        }

        private void advance() {
            index++;
            offset++;
            next = null;
        }
    }

    public record ResultPage(List<SearchImageData> items, Long nextOffset, Long nextClipOffset) {}
}
