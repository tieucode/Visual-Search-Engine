package com.visualsearch.service.search;

import com.visualsearch.client.QdrantSearchClient;
import com.visualsearch.client.QdrantSearchClient.ClipFrame;
import com.visualsearch.client.QdrantSearchClient.ScoredImage;
import com.visualsearch.config.JwtProperties;
import com.visualsearch.dto.search.SearchImageData;
import com.visualsearch.entity.Image;
import com.visualsearch.enums.MediaKind;
import com.visualsearch.enums.SearchType;
import com.visualsearch.exception.BadRequestException;
import com.visualsearch.repository.ImageRepository;
import com.visualsearch.service.ImageUrlResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MixedSimilaritySearchTest {
    private static final UUID CLIP = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID IMAGE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID HIGHER_IMAGE = UUID.randomUUID();
    private static final UUID DELETED = UUID.randomUUID();
    private static final List<Float> QUERY = List.of(0f, 1f);
    private final QdrantSearchClient qdrant = mock(QdrantSearchClient.class);
    private final ImageRepository images = mock(ImageRepository.class);
    private final ImageUrlResolver urls = mock(ImageUrlResolver.class);
    private final SimilaritySearchService search = new SimilaritySearchService(qdrant, images, urls);

    @Test
    void mergesBothOffsetsAndUsesTheBestFrameForClipPreview() {
        var clip = media(CLIP, MediaKind.VIDEO);
        when(images.findSearchableMedia(anyList())).thenAnswer(call -> {
            List<UUID> ids = call.getArgument(0);
            return ids.stream().filter(id -> !id.equals(DELETED))
                    .map(id -> id.equals(CLIP) ? clip : media(id, MediaKind.IMAGE)).toList();
        });
        when(urls.imageUrl(anyString())).thenAnswer(call -> call.getArgument(0));
        when(urls.thumbnailUrl(anyString(), anyString())).thenAnswer(call -> call.getArgument(0));
        when(qdrant.searchImages(QUERY, List.of(), 0, 3)).thenReturn(List.of(
                new ScoredImage(DELETED, .99), new ScoredImage(HIGHER_IMAGE, .9), new ScoredImage(IMAGE, .8)));
        when(qdrant.searchImages(QUERY, List.of(), 2, 3)).thenReturn(List.of(new ScoredImage(IMAGE, .8)));
        when(qdrant.searchClips(QUERY, List.of(), 0, 3)).thenReturn(List.of(new ScoredImage(CLIP, .8)));
        when(qdrant.getClipFrames(List.of(CLIP))).thenReturn(Map.of(CLIP, clipFrames()));

        var first = search.search(QUERY, 0, 0L, 2);
        assertEquals(List.of(HIGHER_IMAGE, CLIP), first.items().stream().map(SearchImageData::imageId).toList());
        assertEquals(1500, first.items().getLast().matchedFrameMs());
        assertEquals("clips/" + CLIP + "/1500.jpg", first.items().getLast().thumbnailUrl());
        assertEquals(2L, first.nextOffset());
        assertEquals(1L, first.nextClipOffset());

        var second = search.search(QUERY, first.nextOffset(), first.nextClipOffset(), 2);
        assertEquals(List.of(IMAGE), second.items().stream().map(SearchImageData::imageId).toList());
        assertNull(second.nextOffset());
    }

    @Test
    void usesTheSelectedClipFrameAndExcludesSourceFromBothCollections() {
        var clip = media(CLIP, MediaKind.GIF);
        when(images.findSearchableMedia(List.of(CLIP))).thenReturn(List.of(clip));
        when(qdrant.getClipFrames(List.of(CLIP))).thenReturn(Map.of(CLIP, clipFrames()));
        search.searchSimilar(clip, 1500, 0, 0L, 2);
        verify(qdrant).searchImages(QUERY, List.of(CLIP), 0, 3);
        verify(qdrant).searchClips(QUERY, List.of(CLIP), 0, 3);
    }

    @Test
    void signsBothOffsetsIncludingAZeroOffsetAndAcceptsLegacyCursors() {
        var properties = new JwtProperties();
        properties.setSecret("a-long-test-secret-for-signing-pagination");
        var pagination = new SearchPaginationService(properties);
        UUID searchId = UUID.randomUUID();
        var result = new SimilaritySearchService.ResultPage(List.of(), 0L, 2L);
        var cursor = pagination.createPage(searchId, SearchType.IMAGE_ONLY, 2, result).nextCursor();
        assertEquals(new SearchPaginationService.Cursor(0, 2L, 2), pagination.decodeCursor(cursor, searchId));
        assertThrows(BadRequestException.class, () -> pagination.decodeCursor(cursor, UUID.randomUUID()));
        var old = pagination.encodeCursor(searchId, 2, 2);
        assertEquals(new SearchPaginationService.Cursor(2, null, 2), pagination.decodeCursor(old, searchId));
    }

    private Image media(UUID id, MediaKind kind) {
        var image = Image.builder().mediaKind(kind).path(id.toString()).build();
        image.setId(id);
        return image;
    }

    private List<ClipFrame> clipFrames() {
        return List.of(new ClipFrame(0, List.of(1f, 0f)), new ClipFrame(1500, QUERY));
    }
}
