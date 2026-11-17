package com.visualsearch.indexing.processing;

import com.visualsearch.indexing.config.IndexingProperties;
import com.visualsearch.indexing.exception.InvalidIndexingMessageException;
import com.visualsearch.indexing.messaging.ImageIndexingMessage;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IndexingMessageValidator {
    private final IndexingProperties properties;

    public List<ImageIndexingMessage.ImageItem> validate(
            ImageIndexingMessage message, int currentAttempt) {
        if (message == null || message.images() == null || message.images().isEmpty()) {
            throw new InvalidIndexingMessageException(
                    "Indexing message must contain at least one image");
        }
        if (message.images().size() > properties.getImage().getMaxBatchImages()) {
            throw new InvalidIndexingMessageException(
                    "Indexing message exceeds max batch size "
                            + properties.getImage().getMaxBatchImages());
        }
        if (currentAttempt < 0 || currentAttempt > properties.getMaxRetries()) {
            throw new InvalidIndexingMessageException("Invalid retry attempt " + currentAttempt);
        }

        Map<UUID, ImageIndexingMessage.ImageItem> unique = new LinkedHashMap<>();
        for (ImageIndexingMessage.ImageItem item : message.images()) {
            if (item == null
                    || item.imageId() == null
                    || item.imageUrl() == null
                    || item.imageUrl().isBlank()) {
                throw new InvalidIndexingMessageException(
                        "Every message item requires imageId and imageUrl");
            }
            ImageIndexingMessage.ImageItem previous = unique.putIfAbsent(item.imageId(), item);
            if (previous != null && !previous.imageUrl().equals(item.imageUrl())) {
                throw new InvalidIndexingMessageException(
                        "Duplicate imageId has conflicting imageUrl: " + item.imageId());
            }
        }
        return List.copyOf(unique.values());
    }
}
