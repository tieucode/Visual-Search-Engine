package com.visualsearch.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record SearchImageData(
        @Schema(format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID imageId,
        @Schema(description = "Original image URL: signed MinIO URL or external HTTP(S) URL", format = "uri", requiredMode = Schema.RequiredMode.REQUIRED)
        String imageUrl,
        @Schema(description = "Thumbnail URL: signed MinIO or external HTTP(S); falls back to imageUrl", format = "uri",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String thumbnailUrl,
        @Schema(description = "Width in pixels, if known", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        Integer width,
        @Schema(description = "Height in pixels, if known", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        Integer height,
        @Schema(description = "Cosine similarity from Qdrant, not a probability", example = "0.83",
                requiredMode = Schema.RequiredMode.REQUIRED)
        double score) {}
