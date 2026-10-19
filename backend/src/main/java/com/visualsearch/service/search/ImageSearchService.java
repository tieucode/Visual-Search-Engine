package com.visualsearch.service.search;

import com.visualsearch.client.AiEmbeddingClient;
import com.visualsearch.config.SearchProperties;
import com.visualsearch.dto.search.SearchPageData;
import com.visualsearch.dto.search.SearchImageData;
import com.visualsearch.entity.Image;
import com.visualsearch.entity.User;
import com.visualsearch.enums.SearchType;
import com.visualsearch.enums.ImageType;
import com.visualsearch.exception.SearchDependencyException;
import com.visualsearch.service.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageSearchService {
    private final QueryImageValidator validator;
    private final AiEmbeddingClient embeddings;
    private final SimilaritySearchService similarity;
    private final SearchHistoryService history;
    private final SearchPaginationService pagination;
    private final MinioStorageService storage;
    private final SearchProperties properties;

<<<<<<< HEAD
    public SearchPageData<SearchImageData> start(MultipartFile file, int size, User user) {
        if (size < 1 || size > 100) {
            throw new BadRequestException("size must be between 1 and 100");
        }
=======
    public SearchPageData start(MultipartFile file, int size, User user) {
        pagination.validatePageSize(size);
>>>>>>> 148ac47b8fa6eaedd855324301e9d9e9cf893b69
        long started = System.nanoTime();
        var query = validator.validate(file);
        var vector = embeddings.embedImage(query.bytes(), query.filename(), query.contentType());
        var results = similarity.search(vector, 0, size);
        String objectName = "search/" + UUID.randomUUID() + "." + query.format().name().toLowerCase(Locale.ROOT);
        try {
            storage.uploadImage(file, objectName);
        } catch (Exception exception) {
            storage.deleteImage(objectName);
            throw new SearchDependencyException("Could not store query image", exception);
        }
        UUID searchId;
        try {
            int duration = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000);
            searchId = history.createImageSearch(user, objectName, query, duration);
        } catch (RuntimeException exception) {
            storage.deleteImage(objectName);
            throw exception;
        }
        return pagination.createPage(searchId, SearchType.IMAGE_ONLY, size, results);
    }

    public SearchPageData<SearchImageData> next(UUID searchId, String cursor, User user) {
        // Ownership is checked before reading storage or calling any expensive dependency.
        Image query = history.getQueryImage(searchId, user);
        var position = pagination.decodeCursor(cursor, searchId);
        if (query.getType() == ImageType.UPLOAD) {
            var results = similarity.searchSimilar(query.getId(), position.offset(), position.size());
            return pagination.createPage(searchId, SearchType.IMAGE_ONLY, position.size(), results);
        }
        byte[] bytes = storage.readImage(query.getPath(), properties.getMaxImageBytes());
        String format = query.getFileFormat().name().toLowerCase(Locale.ROOT);
        var vector = embeddings.embedImage(bytes, "query." + format, "image/" + (format.equals("jpg") ? "jpeg" : format));
        var results = similarity.search(vector, position.offset(), position.size());
        // A continuation never inserts or updates history, even when retried or requested concurrently.
        return pagination.createPage(searchId, SearchType.IMAGE_ONLY, position.size(), results);
    }

<<<<<<< HEAD
    private SearchPageData<SearchImageData> response(UUID searchId, int size, SimilaritySearchService.ResultPage page) {
        String nextCursor = page.nextOffset() == null ? null : cursors.encode(searchId, page.nextOffset(), size);
        return new SearchPageData(searchId, SearchType.IMAGE_ONLY, page.items(), size,
                nextCursor != null, nextCursor);
=======
    public SearchPageData startSimilar(UUID imageId, int size, User user) {
        pagination.validatePageSize(size);
        long started = System.nanoTime();
        Image image = history.getLibraryImage(imageId);
        var results = similarity.searchSimilar(image.getId(), 0, size);
        int duration = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000);
        UUID searchId = history.createSimilarSearch(user, image.getId(), duration);
        return pagination.createPage(searchId, SearchType.IMAGE_ONLY, size, results);
>>>>>>> 148ac47b8fa6eaedd855324301e9d9e9cf893b69
    }
}
