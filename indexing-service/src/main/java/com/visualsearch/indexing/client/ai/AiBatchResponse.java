package com.visualsearch.indexing.client.ai;

import java.util.List;
import java.util.UUID;

public record AiBatchResponse(List<Result> results) {

    public AiBatchResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public record Result(
            UUID imageId,
            String status,
            List<Float> embedding,
            List<OcrItem> ocr,
            ErrorItem error) {

        public List<Float> safeEmbedding() {
            return embedding == null ? List.of() : embedding;
        }

        public List<OcrItem> safeOcr() {
            return ocr == null ? List.of() : ocr;
        }

        public boolean successful() {
            return "SUCCESS".equalsIgnoreCase(status);
        }
    }

    public record OcrItem(
            String text,
            String normalizedText,
            Float confidence,
            BoundingBox boundingBox) {
    }

    public record BoundingBox(Float x, Float y, Float width, Float height) {
    }

    public record ErrorItem(String code, String message, Boolean retryable) {
        public boolean isRetryable() {
            return Boolean.TRUE.equals(retryable);
        }
    }
}
