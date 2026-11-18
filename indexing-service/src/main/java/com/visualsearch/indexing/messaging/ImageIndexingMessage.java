package com.visualsearch.indexing.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record ImageIndexingMessage(List<ImageItem> images) {

    public ImageIndexingMessage {
        images = images == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(images));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageItem(UUID imageId) {}
}
