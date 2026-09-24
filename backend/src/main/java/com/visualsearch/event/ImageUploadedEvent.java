package com.visualsearch.event;

import com.visualsearch.enums.ImageFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadedEvent implements Serializable {

    private UUID batchId;
    private int totalImages;
    private List<ImageItem> images;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageItem implements Serializable {
        private UUID imageId;
        private String imageUrl;
        private ImageFormat fileFormat;
    }
}
