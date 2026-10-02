package com.visualsearch.indexing.image;

import java.util.UUID;

public record PreparedImage(
        UUID imageId,
        byte[] bytes,
        String contentType,
        int width,
        int height) {
}
