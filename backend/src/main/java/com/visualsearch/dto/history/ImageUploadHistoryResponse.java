package com.visualsearch.dto.history;

import java.time.Instant;
import java.util.UUID;
import com.visualsearch.enums.MediaKind;

public record ImageUploadHistoryResponse(
        UUID id,
        String imageUrl,
        String thumbnailUrl,
        Integer width,
        Integer height,
        Long fileSize,
        String fileFormat,
        String type,
        Instant createdAt,
        MediaKind mediaKind,
        Integer durationMs
) {
}
