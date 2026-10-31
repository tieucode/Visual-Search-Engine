package com.visualsearch.indexing.messaging;

import java.util.List;
import java.util.UUID;

public record ImageIndexingMessage(List<ImageItem> images) {

    public ImageIndexingMessage {
        images = images == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(images));
    }

    public record ImageItem(UUID imageId, String imageUrl) {
    }
}
