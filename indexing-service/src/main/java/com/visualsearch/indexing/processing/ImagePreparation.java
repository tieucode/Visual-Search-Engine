package com.visualsearch.indexing.processing;

import com.visualsearch.indexing.image.BoundedHttpImageSource;
import com.visualsearch.indexing.image.ImagePreprocessor;
import com.visualsearch.indexing.image.PreparedImage;
import com.visualsearch.indexing.exception.TransientDependencyException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImagePreparation {
    private final BoundedHttpImageSource source;
    private final ImagePreprocessor preprocessor;
    private final MinioClient minio;

    @Value("${minio.bucket-images}")
    private String bucket;

    public PreparedImage prepare(UUID imageId, String path) {
        String url = path;
        if (!path.startsWith("https://") && !path.startsWith("http://")) {
            try {
                url = minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET).bucket(bucket).object(path).expiry(5, TimeUnit.MINUTES).build());
            } catch (Exception exception) {
                throw new TransientDependencyException("Could not sign image download", exception);
            }
        }
        return preprocessor.preprocess(imageId, source.download(url));
    }
}
