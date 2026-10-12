package com.visualsearch.dto.search;

import java.util.UUID;

public record SearchImageData(UUID imageId, String imageUrl, String thumbnailUrl,
                              Integer width, Integer height, double score) {}
