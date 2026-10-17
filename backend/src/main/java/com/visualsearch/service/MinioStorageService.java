package com.visualsearch.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.GetObjectArgs;
import com.visualsearch.exception.SearchDependencyException;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;

    private MinioClient publicSigningClient;

    @Value("${minio.public-endpoint:${minio.endpoint}}")
    private String publicEndpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.region:us-east-1}")
    private String region;

    @Value("${minio.bucket-images}")
    private String bucketImages;

    @Value("${minio.bucket-thumbnails}")
    private String bucketThumbnails;

    @Value("${app.upload.thumbnail-width-px:400}")
    private int thumbnailWidthPx;

    @Value("${minio.presigned-url-expiry-seconds:604800}")
    private int presignedUrlExpirySeconds;

    @PostConstruct
    public void initBuckets() {
        // Explicit region makes signing local; containers need not reach the browser's public hostname.
        publicSigningClient = MinioClient.builder().endpoint(publicEndpoint).region(region)
                .credentials(accessKey, secretKey).build();
        createBucketIfNotExists(bucketImages);
        createBucketIfNotExists(bucketThumbnails);
    }

    private void createBucketIfNotExists(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Bucket '{}' created successfully.", bucketName);
            }
        } catch (Exception e) {
            log.error("Failed to check or create bucket '{}': {}", bucketName, e.getMessage(), e);
        }
    }

    public String uploadImage(MultipartFile file, String objectName) throws Exception {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketImages)
                            .object(objectName)
                            .stream(is, file.getSize(), -1)
                            .contentType(contentType)
                            .build());
        }
        return objectName;
    }

    // Tạo thumbnail và upload lên bucket thumbnails.
    public String uploadThumbnail(MultipartFile file, String thumbnailObjectName) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (InputStream is = file.getInputStream()) {
            Thumbnails.of(is)
                    .size(thumbnailWidthPx, thumbnailWidthPx)
                    .outputFormat("jpg")
                    .outputQuality(0.85)
                    .toOutputStream(outputStream);
        }

        byte[] thumbBytes = outputStream.toByteArray();
        try (ByteArrayInputStream is = new ByteArrayInputStream(thumbBytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketThumbnails)
                            .object(thumbnailObjectName)
                            .stream(is, thumbBytes.length, -1)
                            .contentType("image/jpeg")
                            .build());
        }
        return thumbnailObjectName;
    }

    public String getImageUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketImages)
                            .object(objectName)
                            .expiry(presignedUrlExpirySeconds)
                            .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create image download URL", exception);
        }
    }

    public String getPresignedThumbnailUrl(String objectName) {
        return getPublicDownloadUrl(bucketThumbnails, objectName);
    }

    public String getPublicImageUrl(String objectName) {
        return getPublicDownloadUrl(bucketImages, objectName);
    }

    private String getPublicDownloadUrl(String bucket, String objectName) {
        try {
            return publicSigningClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(bucket).object(objectName)
                    .expiry(presignedUrlExpirySeconds).build());
        } catch (Exception exception) {
            throw new SearchDependencyException("Could not create image download URL", exception);
        }
    }

    /** Read only a server-owned object key, with a hard bound on allocated bytes. */
    public byte[] readImage(String objectName, int maxBytes) {
        try (InputStream input = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketImages).object(objectName).build())) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length == 0 || bytes.length > maxBytes) {
                throw new SearchDependencyException("Stored query image has an invalid size");
            }
            return bytes;
        } catch (SearchDependencyException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SearchDependencyException("Could not read stored query image", exception);
        }
    }

    public void deleteImage(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketImages)
                            .object(objectName)
                            .build());
        } catch (Exception e) {
            log.warn("Failed to delete image '{}' from MinIO: {}", objectName, e.getMessage());
        }
    }

    public void deleteThumbnail(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketThumbnails)
                            .object(objectName)
                            .build());
        } catch (Exception e) {
            log.warn("Failed to delete thumbnail '{}' from MinIO: {}", objectName, e.getMessage());
        }
    }
}
