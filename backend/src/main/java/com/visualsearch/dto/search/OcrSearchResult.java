package com.visualsearch.dto.search;

import com.visualsearch.entity.ImageOcr;

import java.util.UUID;

public record OcrSearchResult(
        UUID imageId,
        String imageUrl,
        String thumbnailUrl,
        Integer width,
        Integer height,
        String rawText,
        Float confidenceScore) {
}
