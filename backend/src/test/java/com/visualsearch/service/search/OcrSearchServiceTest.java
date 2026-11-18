package com.visualsearch.service.search;

import com.visualsearch.client.ElasticsearchOcrSearchClient;
import com.visualsearch.client.ElasticsearchOcrSearchClient.Hit;
import com.visualsearch.config.JwtProperties;
import com.visualsearch.entity.Image;
import com.visualsearch.entity.User;
import com.visualsearch.enums.ImageType;
import com.visualsearch.repository.ImageRepository;
import com.visualsearch.service.ImageUrlResolver;
import com.visualsearch.service.MinioStorageService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OcrSearchServiceTest {
    @Test
    void resolvesCurrentImageLocationsAndPagesOverVisibleHits() {
        var elasticsearch = mock(ElasticsearchOcrSearchClient.class);
        var storage = mock(MinioStorageService.class);
        var images = mock(ImageRepository.class);
        var history = mock(SearchHistoryService.class);
        var jwt = new JwtProperties();
        jwt.setSecret("ocr-pagination-test-secret");
        var service = new OcrSearchService(elasticsearch, new ImageUrlResolver(storage), history,
                new SearchPaginationService(jwt), images);
        var user = new User();
        UUID searchId = UUID.randomUUID();
        when(history.createOcrSearch(eq(user), eq("toi"), anyInt())).thenReturn(searchId);
        when(history.getOcrQuery(searchId, user)).thenReturn("toi");

        var external = image("https://images.example/a%20b.jpg?sig=test", "http://cdn.example/thumb.jpg");
        var invalid = image("file:///broken.jpg", null);
        var noThumbnail = image("https://images.example/second.jpg", null);
        var local = image("uploads/photo.jpg", "thumbs/photo.jpg");
        when(storage.getPublicImageUrl(local.getPath())).thenReturn("signed-original");
        when(storage.getPresignedThumbnailUrl(local.getThumbnailPath())).thenReturn("signed-thumb");
        var all = List.of(external, invalid, noThumbnail, local);
        when(images.findByIdInAndType(anyList(), eq(ImageType.UPLOAD))).thenReturn(all);
        when(elasticsearch.search("toi", 32, null, null)).thenReturn(
                List.of(hit(external, 5), hit(invalid, 4), hit(noThumbnail, 3), hit(local, 2)));
        when(elasticsearch.search("toi", 32, 3.0, noThumbnail.getId())).thenReturn(List.of(hit(local, 2)));

        var first = service.start("tôi", 2, user);
        assertThat(first.hasMore()).isTrue();
        assertThat(first.items()).hasSize(2);
        assertThat(first.items().getFirst().imageUrl()).isEqualTo(external.getPath());
        assertThat(first.items().getFirst().thumbnailUrl()).isEqualTo(external.getThumbnailPath());
        assertThat(first.items().getFirst().rawTexts()).containsExactly("tôi");
        assertThat(first.items().getLast().thumbnailUrl()).isEqualTo(noThumbnail.getPath());

        var next = service.next(searchId, first.nextCursor(), user);
        assertThat(next.items()).singleElement().satisfies(item -> {
            assertThat(item.imageUrl()).isEqualTo("signed-original");
            assertThat(item.thumbnailUrl()).isEqualTo("signed-thumb");
        });
        assertThat(next.hasMore()).isFalse();
        assertThat(next.nextCursor()).isNull();
    }

    private Image image(String path, String thumbnail) {
        var image = Image.builder().path(path).thumbnailPath(thumbnail).build();
        image.setId(UUID.randomUUID());
        return image;
    }

    private Hit hit(Image image, double rank) {
        return new Hit(image.getId(), 0.9, List.of("tôi"), rank, image.getId());
    }
}
