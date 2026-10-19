package com.visualsearch.repository.projection;

import java.util.UUID;

/** Read-only projection returned by the paginated OCR search query. */
public interface OcrSearchProjection {
    UUID getImageId();
    String getImagePath();
    String getThumbnailPath();
    Integer getWidth();
    Integer getHeight();
    Float getConfidenceScore();
    String getRawTexts();
}
