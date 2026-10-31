package com.visualsearch.indexing.processing;
import com.visualsearch.indexing.image.*;
import com.visualsearch.indexing.messaging.ImageIndexingMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class ImagePreparation {
    private final BoundedHttpImageSource source;
    private final ImagePreprocessor preprocessor;
    public PreparedImage prepare(ImageIndexingMessage.ImageItem item) {
        return preprocessor.preprocess(item.imageId(), source.download(item.imageUrl()));
    }
}
