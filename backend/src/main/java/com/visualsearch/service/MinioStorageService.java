package com.visualsearch.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.GetPresignedObjectUrlArgs;
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

    @Value("${minio.endpoint}")
    private String endpoint;

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
                    .outputQuality(0.85)
                    .toOutputStream(outputStream);
        }

        byte[] thumbBytes = outputStream.toByteArray();
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "image/jpeg";
        }

        try (ByteArrayInputStream is = new ByteArrayInputStream(thumbBytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketThumbnails)
                            .object(thumbnailObjectName)
                            .stream(is, thumbBytes.length, -1)
                            .contentType(contentType)
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

    public String getThumbnailUrl(String objectName) {
        String base = endpoint.replaceAll("/+$", "");
        return base + "/" + bucketThumbnails + "/" + objectName;
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
