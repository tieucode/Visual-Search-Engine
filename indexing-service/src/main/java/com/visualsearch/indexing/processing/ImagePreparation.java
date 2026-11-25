package com.visualsearch.indexing.processing;

import com.visualsearch.indexing.image.BoundedHttpImageSource;
import com.visualsearch.indexing.image.ImagePreprocessor;
import com.visualsearch.indexing.image.PreparedImage;
import com.visualsearch.indexing.image.ClipFrameExtractor;
import com.visualsearch.indexing.image.ClipFrameExtractor.Frame;
import com.visualsearch.indexing.exception.TransientDependencyException;
import com.visualsearch.indexing.persistence.ImageState;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImagePreparation {
    private final BoundedHttpImageSource source;
    private final ImagePreprocessor preprocessor;
    private final MinioClient minio;
    private final ClipFrameExtractor clips;

    @Value("${minio.bucket-images}")
    private String bucket;

    public record PreparedMedia(PreparedImage image, List<Frame> frames) {}

    public PreparedMedia prepare(ImageState state) {
        var downloaded = source.download(url(state.imagePath()));
        if (state.isClip()) {
            return new PreparedMedia(null, clips.extract(state.imageId(), downloaded.bytes(),
                    "GIF".equals(state.mediaKind()), state.durationMs()));
        }
        return new PreparedMedia(preprocessor.preprocess(state.imageId(), downloaded), List.of());
    }

    private String url(String path) {
        String url = path;
        if (!path.startsWith("https://") && !path.startsWith("http://")) {
            try {
                url = minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET).bucket(bucket).object(path).expiry(5, TimeUnit.MINUTES).build());
            } catch (Exception exception) {
                throw new TransientDependencyException("Could not sign image download", exception);
            }
        }
        return url;
    }
}
