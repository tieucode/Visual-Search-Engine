package com.visualsearch.service.search;

import com.visualsearch.entity.Image;
import com.visualsearch.entity.SearchHistory;
import com.visualsearch.entity.User;
import com.visualsearch.enums.ImageType;
import com.visualsearch.enums.SearchType;
import com.visualsearch.exception.ResourceNotFoundException;
import com.visualsearch.repository.ImageRepository;
import com.visualsearch.repository.SearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SearchHistoryService {
    private final ImageRepository images;
    private final SearchHistoryRepository histories;

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
