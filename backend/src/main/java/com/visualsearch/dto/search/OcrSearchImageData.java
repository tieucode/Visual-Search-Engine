package com.visualsearch.dto.search;

import java.util.List;
import java.util.UUID;

public record OcrSearchImageData(UUID imageId, String imageUrl, String thumbnailUrl,
                                 Integer width, Integer height, double score,
                                 List<String> rawTexts) {}
