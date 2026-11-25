package com.visualsearch.indexing.persistence;

import com.visualsearch.indexing.processing.OcrData;

import java.util.List;
import java.util.UUID;

public record IndexWrite(
        UUID imageId, IndexStatus status, int attempt, String error, List<OcrData> ocr, boolean clip) {
    public IndexWrite(UUID imageId, IndexStatus status, int attempt, String error, List<OcrData> ocr) {
        this(imageId, status, attempt, error, ocr, false);
    }

    public IndexWrite {
        ocr = ocr == null ? List.of() : List.copyOf(ocr);
    }
}
