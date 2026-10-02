package com.visualsearch.indexing.processing;

public record OcrData(
        String text,
        String normalizedText,
        float confidence,
        BoundingBox boundingBox) {

    public record BoundingBox(float x, float y, float width, float height) {
    }
}
