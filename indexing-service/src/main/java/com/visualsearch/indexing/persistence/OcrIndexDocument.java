package com.visualsearch.indexing.persistence;

import java.util.List;
import java.util.UUID;

public record OcrIndexDocument(
        UUID imageId,
        String imagePath,
        String thumbnailPath,
        Integer width,
        Integer height,
        List<String> normalizedTexts,
        List<String> rawTexts,
        Double confidenceScore) {}
