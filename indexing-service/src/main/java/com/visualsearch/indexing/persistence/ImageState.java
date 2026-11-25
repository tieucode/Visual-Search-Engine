package com.visualsearch.indexing.persistence;

import java.util.UUID;

public record ImageState(UUID imageId, UUID batchId, IndexStatus status, String imagePath,
                         String mediaKind, Integer durationMs) {
    public boolean isClip() {
        return "GIF".equals(mediaKind) || "VIDEO".equals(mediaKind);
    }
}
