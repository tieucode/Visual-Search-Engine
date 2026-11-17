package com.visualsearch.indexing.persistence;

import com.visualsearch.indexing.processing.OcrData;

import java.util.List;
import java.util.UUID;

public record IndexWrite(
        UUID imageId, IndexStatus status, int attempt, String error, List<OcrData> ocr) {
    public IndexWrite {
        ocr = ocr == null ? List.of() : List.copyOf(ocr);
    }
}
