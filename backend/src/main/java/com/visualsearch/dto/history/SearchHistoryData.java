package com.visualsearch.dto.history;

import com.visualsearch.enums.SearchType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A search previously performed by the authenticated user")
public record SearchHistoryData(
        @Schema(example = "b15ed1c1-74ec-4d76-a935-4f99527da651") UUID searchId,
        @Schema(example = "TEXT_OCR") SearchType searchType,
        @Schema(description = "Text query; null for image-only search", example = "xin chao") String queryText,
        @Schema(description = "Presigned URL of the query image; null for text search") String queryImageUrl,
        @Schema(description = "Presigned URL of the query image thumbnail; null for text search") String queryThumbnailUrl,
        @Schema(example = "142") Integer executionDurationMs,
        @Schema(example = "2026-10-19T11:03:44Z") Instant createdAt
) {
}
