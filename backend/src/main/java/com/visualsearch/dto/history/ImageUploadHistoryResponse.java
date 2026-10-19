package com.visualsearch.dto.history;

import java.time.Instant;
import java.util.UUID;

public record ImageUploadHistoryResponse(
        UUID id,
        String imageUrl,
        String thumbnailUrl,
        Integer width,
        Integer height,
        Long fileSize,
        String fileFormat,
        String type,
        Instant createdAt
) {
}
