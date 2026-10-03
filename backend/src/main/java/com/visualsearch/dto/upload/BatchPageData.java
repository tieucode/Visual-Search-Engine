package com.visualsearch.dto.upload;

import java.util.List;

/** Stable JSON shape for the upload page's paged batch modal. */
public record BatchPageData(
        List<BatchSummaryData> content,
        int totalPages,
        long totalElements,
        int number) {
}
