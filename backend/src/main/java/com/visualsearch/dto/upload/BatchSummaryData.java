package com.visualsearch.dto.upload;

import com.visualsearch.enums.BatchStatus;

import java.time.Instant;
import java.util.UUID;

/** Compact batch progress shown by the upload page. */
public record BatchSummaryData(
        UUID batchId,
        int processedImages,
        int totalImages,
        BatchStatus status,
        Instant createdAt) {
}
