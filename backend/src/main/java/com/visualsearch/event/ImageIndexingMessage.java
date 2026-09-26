package com.visualsearch.event;

import java.util.List;
import java.util.UUID;

public record ImageIndexingMessage(List<ImageItem> images) {

    public ImageIndexingMessage {
        images = images == null ? List.of() : List.copyOf(images);
    }

    public record ImageItem(UUID imageId, String imageUrl) {
    }
}
