package com.visualsearch.service.search;

import com.visualsearch.dto.history.SearchHistoryData;
import com.visualsearch.dto.history.SearchHistoryPageData;
import com.visualsearch.entity.Image;
import com.visualsearch.entity.SearchHistory;
import com.visualsearch.entity.User;
import com.visualsearch.enums.ImageType;
import com.visualsearch.enums.SearchType;
import com.visualsearch.exception.BadRequestException;
import com.visualsearch.exception.ResourceNotFoundException;
import com.visualsearch.repository.ImageRepository;
import com.visualsearch.repository.SearchHistoryRepository;
import com.visualsearch.service.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SearchHistoryService {
    private final ImageRepository images;
    private final SearchHistoryRepository histories;
    private final MinioStorageService storage;

    @Transactional(readOnly = true)
    public SearchHistoryPageData getHistory(User user, SearchType searchType, int page, int size) {
        if (page < 0) throw new BadRequestException("page must be zero or greater");
        if (size < 1 || size > 100) throw new BadRequestException("size must be between 1 and 100");

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<SearchHistory> result = histories.findHistoryForUser(user.getId(), searchType, pageable);
        List<SearchHistoryData> items = result.getContent().stream().map(this::toHistoryData).toList();
        return new SearchHistoryPageData(items, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.hasNext(), result.hasPrevious());
    }

    private SearchHistoryData toHistoryData(SearchHistory history) {
        Image queryImage = history.getQueryImage();
        String imageUrl = queryImage == null ? null : storage.getPublicImageUrl(queryImage.getPath());
        String thumbnailUrl = queryImage == null ? null
                : queryImage.getThumbnailPath() == null ? imageUrl
                : storage.getPresignedThumbnailUrl(queryImage.getThumbnailPath());
        return new SearchHistoryData(history.getId(), history.getSearchType(), history.getQueryText(),
                imageUrl, thumbnailUrl, history.getExecutionDurationMs(), history.getCreatedAt());
    }

    // Keep the DB transaction short: no HTTP, model inference or object storage inside it.
    @Transactional
    public UUID createImageSearch(User user, String objectName, QueryImageValidator.QueryImage query, int durationMs) {
        Image image = images.save(Image.builder().path(objectName).uploadedBy(user)
                .fileFormat(query.format()).fileSize((long) query.bytes().length)
                .width(query.width()).height(query.height()).type(ImageType.SEARCH).build());
        SearchHistory history = histories.save(SearchHistory.builder().user(user)
                .searchType(SearchType.IMAGE_ONLY).queryImage(image).executionDurationMs(durationMs).build());
        return history.getId();
    }

    @Transactional
    public UUID createOcrSearch(User user, String query, int durationMs) {
        SearchHistory history = histories.save(SearchHistory.builder().user(user)
                .searchType(SearchType.TEXT_OCR).queryText(query).executionDurationMs(durationMs).build());
        return history.getId();
    }

    @Transactional(readOnly = true)
    public String getOcrQuery(UUID searchId, User user) {
        SearchHistory history = histories.findByIdAndUserIdAndSearchType(searchId, user.getId(), SearchType.TEXT_OCR)
                .orElseThrow(() -> new ResourceNotFoundException("OCR search not found"));
        return history.getQueryText();
    }

    @Transactional(readOnly = true)
    public Image getQueryImage(UUID searchId, User user) {
        SearchHistory history = histories.findByIdAndUserIdAndSearchType(searchId, user.getId(), SearchType.IMAGE_ONLY)
                .orElseThrow(() -> new ResourceNotFoundException("Image search not found"));
        if (history.getQueryImage() == null || history.getQueryImage().getType() != ImageType.SEARCH) {
            throw new ResourceNotFoundException("Query image is no longer available");
        }
        return history.getQueryImage();
    }
}
